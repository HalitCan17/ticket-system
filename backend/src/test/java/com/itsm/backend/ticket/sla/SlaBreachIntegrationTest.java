package com.itsm.backend.ticket.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import com.itsm.backend.notification.Notification;
import com.itsm.backend.notification.NotificationRepository;
import com.itsm.backend.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** SLA breach notifications against the real database, running the check "in the future". */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SlaBreachIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    SlaBreachService breachService;

    @Autowired
    NotificationRepository notifications;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    final String agent = "agent-" + suffix;
    final String manager = "manager-" + suffix;

    @BeforeEach
    void managerHasSignedInOnce() throws Exception {
        // Managers are known to the directory from their tokens.
        mvc.perform(get("/api/tickets").with(TestTokens.user(manager, "MANAGER"))).andExpect(status().isOk());
    }

    @Test
    void breachIsNotifiedOnceToAssigneeAndManagers() throws Exception {
        Instant openedAt = openCriticalTicketStartedBy(agent); // 4h target

        breachService.notifyBreaches(openedAt.plus(Duration.ofHours(3)));
        assertThat(slaNotificationsFor(agent)).isEmpty();

        breachService.notifyBreaches(openedAt.plus(Duration.ofHours(5)));
        breachService.notifyBreaches(openedAt.plus(Duration.ofHours(6))); // already notified

        assertThat(slaNotificationsFor(agent)).hasSize(1);
        assertThat(slaNotificationsFor(manager)).hasSize(1);
        assertThat(slaNotificationsFor(customer)).isEmpty();
    }

    @Test
    void pausedTicketDoesNotBreachWhileWaitingForTheCustomer() throws Exception {
        Instant openedAt = openCriticalTicketStartedBy(agent);
        long id = lastTicketId;
        mvc.perform(post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"WAITING_FOR_CUSTOMER\"}")
                        .with(TestTokens.user(agent, "AGENT")))
                .andExpect(status().isOk());

        breachService.notifyBreaches(openedAt.plus(Duration.ofDays(3)));

        assertThat(slaNotificationsFor(agent)).isEmpty();
    }

    @Test
    void resolvedTicketIsNotNotified() throws Exception {
        Instant openedAt = openCriticalTicketStartedBy(agent);
        mvc.perform(post("/api/tickets/{id}/transitions", lastTicketId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"RESOLVED\",\"note\":\"done\"}")
                        .with(TestTokens.user(agent, "AGENT")))
                .andExpect(status().isOk());

        breachService.notifyBreaches(openedAt.plus(Duration.ofDays(3)));

        assertThat(slaNotificationsFor(agent)).isEmpty();
    }

    long lastTicketId;

    private Instant openCriticalTicketStartedBy(String agentId) throws Exception {
        String body = mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sunucu çöktü\",\"description\":\"x\",\"priority\":\"CRITICAL\",\"category\":\"OTHER\"}")
                        .with(TestTokens.user(customer, "CUSTOMER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode ticket = json.readTree(body);
        lastTicketId = ticket.get("id").asLong();
        mvc.perform(post("/api/tickets/{id}/transitions", lastTicketId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"IN_PROGRESS\"}")
                        .with(TestTokens.user(agentId, "AGENT")))
                .andExpect(status().isOk());
        return Instant.parse(ticket.get("createdAt").asText());
    }

    private List<Notification> slaNotificationsFor(String userId) {
        return notifications.findByRecipientId(userId, Pageable.ofSize(50)).stream()
                .filter(n -> n.getType() == NotificationType.SLA_BREACHED && n.getTicketId() == lastTicketId)
                .toList();
    }
}
