package com.itsm.backend.ticket.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import com.itsm.backend.notification.Notification;
import com.itsm.backend.notification.NotificationRepository;
import com.itsm.backend.notification.NotificationService;
import com.itsm.backend.notification.NotificationType;
import com.itsm.backend.ticket.repository.TicketActivityRepository;
import com.itsm.backend.ticket.repository.TicketRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

/** Aşama 15a: first response SLA, 80 % warning, escalation (docs/adr/0015-sla-extensions.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SlaExtensionsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    SlaBreachService monitor;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    TicketRepository tickets;

    @Autowired
    TicketActivityRepository activities;

    @Autowired
    NotificationService notificationService;

    @Autowired
    PlatformTransactionManager transactionManager;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    final String agent = "agent-" + suffix;

    @BeforeEach
    void signInAgent() throws Exception {
        perform(get("/api/notifications/unread-count"), agent, "AGENT").andExpect(status().isOk());
    }

    @Test
    void onlyCustomerVisibleStaffReactionsCountAsFirstResponse() throws Exception {
        long id = create("HIGH");
        ticket(id).andExpect(jsonPath("$.firstResponse.respondedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.firstResponse.remainingSeconds").isNumber())
                .andExpect(jsonPath("$.firstResponse.breached").value(false));

        comment(id, agent, "AGENT", "INTERNAL", "Dahili not");
        comment(id, customer, "CUSTOMER", "EXTERNAL", "Ek bilgi: Windows 11");
        ticket(id).andExpect(jsonPath("$.firstResponse.respondedAt").value(Matchers.nullValue()));

        comment(id, agent, "AGENT", "EXTERNAL", "Merhaba, inceliyoruz.");
        ticket(id).andExpect(jsonPath("$.firstResponse.respondedAt").isNotEmpty())
                .andExpect(jsonPath("$.firstResponse.remainingSeconds").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.firstResponse.breached").value(false));

        long resolvedDirectly = create("LOW");
        transition(resolvedDirectly, "IN_PROGRESS", null);
        ticket(resolvedDirectly).andExpect(jsonPath("$.firstResponse.respondedAt").value(Matchers.nullValue()));
        transition(resolvedDirectly, "RESOLVED", "Uzaktan düzeltildi");
        ticket(resolvedDirectly).andExpect(jsonPath("$.firstResponse.respondedAt").isNotEmpty());
    }

    @Test
    void monitorReportsLateFirstResponseWarnsAndEscalatesEachOnce() throws Exception {
        Instant openedAt = Instant.now();
        long id = create("CRITICAL"); // first response 30 min, resolution 4 h
        assign(id, agent);

        monitor.run(openedAt.plus(Duration.ofMinutes(20)));
        assertThat(types()).isEmpty();

        monitor.run(openedAt.plus(Duration.ofMinutes(40)));
        monitor.run(openedAt.plus(Duration.ofMinutes(50)));
        assertThat(types()).containsExactly(NotificationType.FIRST_RESPONSE_BREACHED);
        // (The API reports the clocks at the real "now", so the simulated lateness is not visible there.)

        monitor.run(openedAt.plus(Duration.ofMinutes(200))); // 80 % of 4 h = 192 min
        monitor.run(openedAt.plus(Duration.ofMinutes(210)));
        assertThat(types()).containsExactlyInAnyOrder(NotificationType.FIRST_RESPONSE_BREACHED,
                NotificationType.SLA_WARNING);
        ticket(id).andExpect(jsonPath("$.escalatedAt").value(Matchers.nullValue()));

        monitor.run(openedAt.plus(Duration.ofHours(5)));
        monitor.run(openedAt.plus(Duration.ofHours(6)));
        assertThat(types()).containsExactlyInAnyOrder(NotificationType.FIRST_RESPONSE_BREACHED,
                NotificationType.SLA_WARNING, NotificationType.SLA_BREACHED);
        ticket(id).andExpect(jsonPath("$.escalatedAt").isNotEmpty())
                .andExpect(jsonPath("$.priority").value("CRITICAL")); // raising is off by default

        perform(get("/api/tickets").param("escalated", "true").param("q", "#" + id), agent, "AGENT")
                .andExpect(jsonPath("$.items[0].id").value(id));
        perform(get("/api/tickets").param("escalated", "true").param("q", "#" + id), customer, "CUSTOMER")
                .andExpect(jsonPath("$.items[0].id").value(id)); // customers ignore the staff filter
    }

    @Test
    void answeredAndWaitingTicketsAreLeftAlone() throws Exception {
        Instant openedAt = Instant.now();
        long answered = create("CRITICAL");
        assign(answered, agent);
        comment(answered, agent, "AGENT", "EXTERNAL", "Bakıyoruz.");
        transition(answered, "IN_PROGRESS", null);
        transition(answered, "WAITING_FOR_CUSTOMER", null); // clock paused: no warning, no breach

        monitor.run(openedAt.plus(Duration.ofHours(10)));
        assertThat(types()).isEmpty();
    }

    @Test
    void escalationRaisesThePriorityWhenConfigured() throws Exception {
        SlaBreachService raising = new SlaBreachService(tickets, activities, notificationService,
                new SlaProperties(null, null, new SlaProperties.Escalation(true)), transactionManager);
        Instant openedAt = Instant.now();
        long id = create("HIGH"); // 8 h
        assign(id, agent);

        raising.notifyBreaches(openedAt.plus(Duration.ofHours(9)));

        ticket(id).andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.sla.targetSeconds").value(4 * 3600))
                .andExpect(jsonPath("$.escalatedAt").isNotEmpty());
        perform(get("/api/tickets/{id}/activities", id), "manager1", "MANAGER")
                .andExpect(jsonPath("$[?(@.type == 'PRIORITY_CHANGED')].actorId", Matchers.contains("system")))
                .andExpect(jsonPath("$[?(@.type == 'PRIORITY_CHANGED')].newValue", Matchers.contains("CRITICAL")));
    }

    @Test
    void summaryCountsFirstResponsesAndEscalatedTickets() throws Exception {
        JsonNode before = summary();
        long fast = create("LOW");
        comment(fast, agent, "AGENT", "EXTERNAL", "Hemen bakıyoruz.");
        JsonNode after = summary();
        assertThat(after.at("/firstResponse/responded").asLong() - before.at("/firstResponse/responded").asLong())
                .isEqualTo(1);
        assertThat(after.at("/firstResponse/withinTarget").asLong()
                - before.at("/firstResponse/withinTarget").asLong()).isEqualTo(1);
        assertThat(after.has("escalatedOpen")).isTrue();
    }

    private List<NotificationType> types() {
        return notifications.findByRecipientId(agent, Pageable.unpaged()).stream()
                .map(Notification::getType)
                .filter(type -> type != NotificationType.TICKET_ASSIGNED)
                .toList();
    }

    private JsonNode summary() throws Exception {
        return json.readTree(perform(get("/api/reports/summary"), "manager1", "MANAGER")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long create(String priority) throws Exception {
        String body = json.writeValueAsString(Map.of("title", "SLA " + suffix, "description", "x",
                "priority", priority, "category", "OTHER"));
        return json.readTree(perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body),
                customer, "CUSTOMER").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ResultActions ticket(long id) throws Exception {
        return perform(get("/api/tickets/{id}", id), agent, "AGENT").andExpect(status().isOk());
    }

    private void assign(long id, String assigneeId) throws Exception {
        perform(put("/api/tickets/{id}/assignee", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assigneeId", assigneeId))), "manager1", "MANAGER")
                .andExpect(status().isOk());
    }

    private void comment(long id, String userId, String role, String visibility, String body) throws Exception {
        perform(post("/api/tickets/{id}/comments", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("body", body, "visibility", visibility))), userId, role)
                .andExpect(status().isCreated());
    }

    private void transition(long id, String target, String note) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("targetStatus", target);
        if (note != null) {
            body.put("note", note);
        }
        perform(post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), agent, "AGENT").andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
