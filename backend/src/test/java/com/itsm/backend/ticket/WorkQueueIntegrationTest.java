package com.itsm.backend.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Aşama 11: assignment, priority/category changes and the staff work queue (docs/adr/0011). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkQueueIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    NotificationRepository notifications;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    final String agent = "agent-a-" + suffix;
    final String otherAgent = "agent-b-" + suffix;
    final String manager = "manager-" + suffix;

    @BeforeEach
    void signInStaff() throws Exception {
        // The user directory learns roles from tokens: staff become assignable after their first request.
        for (String[] user : new String[][] {{agent, "AGENT"}, {otherAgent, "AGENT"}, {manager, "MANAGER"}}) {
            perform(get("/api/notifications/unread-count"), user[0], user[1]).andExpect(status().isOk());
        }
    }

    @Test
    void agentClaimsHandsOverAndCannotTakeOthersTickets() throws Exception {
        long id = create(customer, "MEDIUM");
        ticket(id, agent, "AGENT")
                .andExpect(jsonPath("$.canClaim").value(true))
                .andExpect(jsonPath("$.canReassign").value(false))
                .andExpect(jsonPath("$.canEdit").value(true));
        ticket(id, customer, "CUSTOMER")
                .andExpect(jsonPath("$.canClaim").value(false))
                .andExpect(jsonPath("$.canEdit").value(false));

        assign(id, agent, "AGENT", otherAgent).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_ALLOWED"));
        assign(id, agent, "AGENT", agent).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(agent))
                .andExpect(jsonPath("$.assigneeName").value(TestTokens.displayName(agent)))
                .andExpect(jsonPath("$.canClaim").value(false))
                .andExpect(jsonPath("$.canReassign").value(true));

        assign(id, otherAgent, "AGENT", otherAgent).andExpect(status().isForbidden());
        assign(id, agent, "AGENT", customer).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_STAFF"));
        assign(id, customer, "CUSTOMER", agent).andExpect(status().isForbidden());

        assign(id, agent, "AGENT", otherAgent).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(otherAgent));
        // Same assignee again: nothing changes, nothing is recorded.
        assign(id, manager, "MANAGER", otherAgent).andExpect(status().isOk());

        activities(id, manager, "MANAGER")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[0].oldValue").value(nullValue()))
                .andExpect(jsonPath("$[0].newName").value(TestTokens.displayName(agent)))
                .andExpect(jsonPath("$[1].oldValue").value(agent))
                .andExpect(jsonPath("$[1].newValue").value(otherAgent))
                .andExpect(jsonPath("$[1].actorName").value(TestTokens.displayName(agent)));
        // Customers never see the staff log.
        activities(id, customer, "CUSTOMER").andExpect(status().isNotFound());

        // otherAgent was told about the hand-over; agent (who did it) was not told anything about themselves.
        assertThat(types(otherAgent)).contains(NotificationType.TICKET_ASSIGNED);
        assertThat(types(agent)).doesNotContain(NotificationType.TICKET_ASSIGNED, NotificationType.TICKET_UNASSIGNED);
    }

    @Test
    void managerAssignsAnyoneAndUnassigns() throws Exception {
        long id = create(customer, "LOW");
        assign(id, manager, "MANAGER", agent).andExpect(status().isOk());
        assign(id, manager, "MANAGER", otherAgent).andExpect(status().isOk());
        assign(id, manager, "MANAGER", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(nullValue()));

        assertThat(types(agent)).contains(NotificationType.TICKET_ASSIGNED, NotificationType.TICKET_UNASSIGNED);
        assertThat(types(otherAgent)).contains(NotificationType.TICKET_ASSIGNED, NotificationType.TICKET_UNASSIGNED);

        transition(id, customer, "CUSTOMER", "CLOSED"); // withdrawn
        assign(id, manager, "MANAGER", agent).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_CLOSED"));
    }

    @Test
    void staffChangePriorityWithReasonAndCategory() throws Exception {
        long id = create(customer, "LOW");
        assign(id, manager, "MANAGER", agent).andExpect(status().isOk());

        update(id, agent, "AGENT", Map.of("priority", "CRITICAL")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIORITY_REASON_REQUIRED"));
        update(id, customer, "CUSTOMER", Map.of("category", "NETWORK")).andExpect(status().isForbidden());
        update(id, agent, "AGENT", Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_UPDATE"));

        update(id, manager, "MANAGER", Map.of("priority", "CRITICAL", "category", "NETWORK", "reason", "Üretim durdu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.category").value("NETWORK"))
                .andExpect(jsonPath("$.sla.targetSeconds").value(4 * 3600));

        activities(id, agent, "AGENT")
                .andExpect(jsonPath("$[1].type").value("PRIORITY_CHANGED"))
                .andExpect(jsonPath("$[1].oldValue").value("LOW"))
                .andExpect(jsonPath("$[1].newValue").value("CRITICAL"))
                .andExpect(jsonPath("$[1].note").value("Üretim durdu"))
                .andExpect(jsonPath("$[2].type").value("CATEGORY_CHANGED"))
                .andExpect(jsonPath("$[2].newValue").value("NETWORK"));
        assertThat(types(agent)).contains(NotificationType.PRIORITY_CHANGED);

        transition(id, agent, "AGENT", "IN_PROGRESS");
        transition(id, agent, "AGENT", "RESOLVED", "Tamam");
        update(id, agent, "AGENT", Map.of("priority", "LOW", "reason", "x")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIORITY_LOCKED"));
    }

    @Test
    void workQueueFiltersByAssigneeAndOrdersBySla() throws Exception {
        long low = create(customer, "LOW");            // 72 h
        long critical = create(customer, "CRITICAL");  // 4 h
        long high = create(customer, "HIGH");          // 8 h
        long waiting = create(customer, "CRITICAL");
        assign(low, manager, "MANAGER", agent).andExpect(status().isOk());
        assign(critical, manager, "MANAGER", agent).andExpect(status().isOk());
        assign(high, manager, "MANAGER", agent).andExpect(status().isOk());
        assign(waiting, manager, "MANAGER", agent).andExpect(status().isOk());
        transition(waiting, agent, "AGENT", "IN_PROGRESS");
        transition(waiting, agent, "AGENT", "WAITING_FOR_CUSTOMER"); // paused: after the running ones
        long unassigned = create(customer, "MEDIUM");

        List<Long> mine = ids(list(agent, "AGENT", Map.of("assignee", "me", "sort", "SLA")));
        assertThat(mine).containsExactly(critical, high, low, waiting);
        list(agent, "AGENT", Map.of("assignee", "unassigned", "q", "Kuyruk " + suffix))
                .andExpect(jsonPath("$.items[*].id", contains((int) unassigned)));
        list(manager, "MANAGER", Map.of("assignee", agent, "q", "Kuyruk " + suffix))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[*].assigneeId", everyItem(is(agent))));
        // Customers cannot use the queue filters to look around; they still see only their own tickets.
        list(customer, "CUSTOMER", Map.of("assignee", agent))
                .andExpect(jsonPath("$.items[*].requesterId", everyItem(is(customer))))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) unassigned)));
    }

    @Test
    void staffListIsForStaffOnly() throws Exception {
        perform(get("/api/users/staff"), manager, "MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(agent)))
                .andExpect(jsonPath("$[*].id", hasItem(manager)))
                .andExpect(jsonPath("$[*].id", not(hasItem(customer))))
                .andExpect(jsonPath("$[?(@.id == '" + agent + "')].role", contains("AGENT")));
        perform(get("/api/users/staff"), customer, "CUSTOMER").andExpect(status().isForbidden());
    }

    private List<NotificationType> types(String userId) {
        return notifications.findByRecipientId(userId, Pageable.unpaged()).stream()
                .map(Notification::getType).toList();
    }

    private List<Long> ids(ResultActions result) throws Exception {
        JsonNode items = json.readTree(result.andReturn().getResponse().getContentAsString()).get("items");
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private long create(String requester, String priority) throws Exception {
        String body = json.writeValueAsString(Map.of("title", "Kuyruk " + suffix, "description", "x",
                "priority", priority, "category", "OTHER"));
        String response = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body),
                requester, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions ticket(long id, String userId, String role) throws Exception {
        return perform(get("/api/tickets/{id}", id), userId, role).andExpect(status().isOk());
    }

    private ResultActions assign(long id, String userId, String role, String assigneeId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("assigneeId", assigneeId);
        return perform(put("/api/tickets/{id}/assignee", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, role);
    }

    private ResultActions update(long id, String userId, String role, Map<String, String> body) throws Exception {
        return perform(patch("/api/tickets/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, role);
    }

    private ResultActions activities(long id, String userId, String role) throws Exception {
        return perform(get("/api/tickets/{id}/activities", id), userId, role);
    }

    private ResultActions list(String userId, String role, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/tickets").param("size", "100");
        params.forEach(request::param);
        return perform(request, userId, role).andExpect(status().isOk());
    }

    private void transition(long id, String userId, String role, String target) throws Exception {
        transition(id, userId, role, target, null);
    }

    private void transition(long id, String userId, String role, String target, String note) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("targetStatus", target);
        if (note != null) {
            body.put("note", note);
        }
        perform(post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, role).andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
