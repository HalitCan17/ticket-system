package com.itsm.backend.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.itsm.backend.ticket.domain.TicketCategory;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Aşama 14 (docs/adr/0014-support-groups.md). Category routes are global, so every test restores them;
 * groups and users are created per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GroupRoutingIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    GroupDirectory directory;

    @Autowired
    NotificationRepository notifications;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    // Ids sort as agentA < agentB, which is the round-robin order.
    final String agentA = "agent-a-" + suffix;
    final String agentB = "agent-b-" + suffix;
    Map<TicketCategory, Long> savedRoutes;

    @BeforeEach
    void setUp() throws Exception {
        savedRoutes = directory.routes();
        for (String agent : List.of(agentA, agentB)) {
            perform(get("/api/notifications/unread-count"), agent, "AGENT").andExpect(status().isOk());
        }
    }

    @AfterEach
    void restoreRoutes() {
        directory.replaceRoutes(savedRoutes);
    }

    @Test
    void starterGroupsAreRoutedByCategoryAndVisibleToStaffOnly() throws Exception {
        perform(get("/api/groups"), agentA, "AGENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Ağ ekibi')].categories[0]", contains("NETWORK")))
                .andExpect(jsonPath("$[?(@.name == 'Genel destek')].assignmentMode", contains("NONE")));
        perform(get("/api/groups"), customer, "CUSTOMER").andExpect(status().isForbidden());

        // A new ticket joins the group of its category; the customer sees the group's name.
        long id = create("HARDWARE");
        ticket(id, customer, "CUSTOMER").andExpect(jsonPath("$.groupName").value("Donanım ekibi"));
    }

    @Test
    void onlyManagersChangeGroupsAndNamesAreUnique() throws Exception {
        Map<String, Object> body = Map.of("name", "Sunucu ekibi " + suffix, "assignmentMode", "NONE");
        write(post("/api/groups"), agentA, "AGENT", body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_ONLY"));
        long id = groupId(write(post("/api/groups"), "manager1", "MANAGER", body).andExpect(status().isCreated()));
        // Case-insensitive, also for the Turkish İ (PostgreSQL lower() maps it to i).
        for (String sameName : List.of("sunucu ekibi " + suffix, "SUNUCU EKİBİ " + suffix)) {
            write(post("/api/groups"), "manager1", "MANAGER", Map.of("name", sameName, "assignmentMode", "NONE"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_NAME_TAKEN"));
        }

        write(put("/api/groups/{id}/members", id), "manager1", "MANAGER", Map.of("userIds", List.of(agentA, customer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEMBER_NOT_STAFF"));
        write(put("/api/groups/{id}/members", id), "manager1", "MANAGER", Map.of("userIds", List.of(agentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].id").value(agentA))
                .andExpect(jsonPath("$.members[0].name").value(TestTokens.displayName(agentA)));

        // Deleting a group leaves its tickets without a group; the assignee stays.
        routes(Map.of("SOFTWARE", id));
        long ticket = create("SOFTWARE");
        assign(ticket, agentB);
        perform(delete("/api/groups/{id}", id), "manager1", "MANAGER").andExpect(status().isNoContent());
        ticket(ticket, agentB, "AGENT")
                .andExpect(jsonPath("$.groupId").value(nullValue()))
                .andExpect(jsonPath("$.assigneeId").value(agentB));
        perform(delete("/api/groups/{id}", id), "manager1", "MANAGER").andExpect(status().isNotFound());
    }

    @Test
    void roundRobinTakesTurnsAndTheSystemIsTheActor() throws Exception {
        long group = createGroup("ROUND_ROBIN", agentB, agentA);
        routes(Map.of("NETWORK", group));

        List<String> assignees = new ArrayList<>();
        long first = create("NETWORK");
        for (long id : List.of(first, create("NETWORK"), create("NETWORK"))) {
            assignees.add(json.readTree(ticket(id, "manager1", "MANAGER").andReturn().getResponse()
                    .getContentAsString()).get("assigneeId").asText());
        }
        assertThat(assignees).containsExactly(agentA, agentB, agentA);

        perform(get("/api/tickets/{id}/activities", first), "manager1", "MANAGER")
                .andExpect(jsonPath("$[0].type").value("ASSIGNEE_CHANGED"))
                .andExpect(jsonPath("$[0].actorId").value("system"))
                .andExpect(jsonPath("$[0].newValue").value(agentA));
        assertThat(types(agentA)).contains(NotificationType.TICKET_ASSIGNED);
    }

    @Test
    void leastLoadedPrefersTheMemberWithLessOpenWorkAndNoneOnlyQueues() throws Exception {
        long busy = create("OTHER");
        long busy2 = create("OTHER");
        assign(busy, agentA);
        assign(busy2, agentA);
        long group = createGroup("LEAST_LOADED", agentA, agentB);
        routes(Map.of("ACCESS", group));

        long id = create("ACCESS");
        ticket(id, "manager1", "MANAGER").andExpect(jsonPath("$.assigneeId").value(agentB));

        long queueOnly = createGroup("NONE", agentA);
        routes(Map.of("ACCESS", queueOnly));
        long waiting = create("ACCESS");
        ticket(waiting, "manager1", "MANAGER")
                .andExpect(jsonPath("$.groupId").value(queueOnly))
                .andExpect(jsonPath("$.assigneeId").value(nullValue()));
    }

    @Test
    void groupFollowsCategoryCanBeChangedByHandAndNeverTakesAnAssignedTicket() throws Exception {
        long rr = createGroup("ROUND_ROBIN", agentB);
        long queue = createGroup("NONE");
        routes(Map.of("HARDWARE", queue, "NETWORK", rr));

        long id = create("HARDWARE");
        assign(id, agentA); // assigned before the move
        update(id, agentA, Map.of("category", "NETWORK")).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(rr))
                .andExpect(jsonPath("$.assigneeId").value(agentA)); // round-robin does not take it away

        update(id, agentA, Map.of("groupId", queue)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(queue));
        update(id, agentA, Map.of("removeGroup", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(nullValue()));
        update(id, agentA, Map.of("groupId", 999_999)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        update(id, customer, Map.of("groupId", queue)).andExpect(status().isForbidden());

        perform(get("/api/tickets/{id}/activities", id), "manager1", "MANAGER")
                .andExpect(jsonPath("$[?(@.type == 'GROUP_CHANGED')].newName", hasItem(groupName(rr))))
                .andExpect(jsonPath("$[?(@.type == 'GROUP_CHANGED')].newValue", hasItem(nullValue())));

        // An unassigned ticket moved into a round-robin group is assigned there.
        long fresh = create("HARDWARE");
        update(fresh, "manager1", Map.of("groupId", rr)).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(agentB));
    }

    @Test
    void myGroupsQueueAndGroupWorkload() throws Exception {
        long group = createGroup("NONE", agentA);
        routes(Map.of("SOFTWARE", group));
        long id = create("SOFTWARE");
        long other = create("OTHER");

        JsonNode mine = json.readTree(perform(get("/api/tickets").param("group", "mine").param("size", "100"),
                agentA, "AGENT").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        mine.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        assertThat(ids).contains(id).doesNotContain(other);
        perform(get("/api/tickets").param("group", "mine"), agentB, "AGENT")
                .andExpect(jsonPath("$.totalElements").value(0)); // agentB is in no group
        perform(get("/api/tickets").param("group", "x"), agentB, "AGENT").andExpect(status().isBadRequest());

        perform(get("/api/reports/groups"), "manager1", "MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + group + ")].openWork", contains(1)))
                .andExpect(jsonPath("$[?(@.id == " + group + ")].unassignedOpen", contains(1)))
                .andExpect(jsonPath("$[?(@.id == " + group + ")].members", contains(1)));
        perform(get("/api/reports/groups"), agentA, "AGENT").andExpect(status().isForbidden());
    }

    private long createGroup(String mode, String... members) throws Exception {
        long id = groupId(write(post("/api/groups"), "manager1", "MANAGER",
                Map.of("name", "Grup " + mode + " " + UUID.randomUUID().toString().substring(0, 6),
                        "assignmentMode", mode)).andExpect(status().isCreated()));
        write(put("/api/groups/{id}/members", id), "manager1", "MANAGER", Map.of("userIds", List.of(members)))
                .andExpect(status().isOk());
        return id;
    }

    private String groupName(long id) {
        return directory.names(List.of(id)).get(id);
    }

    private long groupId(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    /** Sets the given routes on top of the saved ones (restored after the test). */
    private void routes(Map<String, Long> changes) throws Exception {
        Map<String, Object> body = new HashMap<>();
        savedRoutes.forEach((category, groupId) -> body.put(category.name(), groupId));
        body.putAll(changes);
        write(put("/api/groups/routes"), "manager1", "MANAGER", body).andExpect(status().isOk());
    }

    private List<NotificationType> types(String userId) {
        return notifications.findByRecipientId(userId, Pageable.unpaged()).stream().map(Notification::getType).toList();
    }

    private long create(String category) throws Exception {
        String body = json.writeValueAsString(Map.of("title", "Grup " + suffix, "description", "x",
                "priority", "MEDIUM", "category", category));
        return json.readTree(perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body),
                customer, "CUSTOMER").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ResultActions ticket(long id, String userId, String role) throws Exception {
        return perform(get("/api/tickets/{id}", id), userId, role).andExpect(status().isOk());
    }

    private void assign(long id, String assigneeId) throws Exception {
        write(put("/api/tickets/{id}/assignee", id), "manager1", "MANAGER", Map.of("assigneeId", assigneeId))
                .andExpect(status().isOk());
    }

    private ResultActions update(long id, String userId, Map<String, Object> body) throws Exception {
        String role = userId.startsWith("cust") ? "CUSTOMER" : userId.startsWith("manager") ? "MANAGER" : "AGENT";
        return write(patch("/api/tickets/{id}", id), userId, role, body);
    }

    private ResultActions write(MockHttpServletRequestBuilder request, String userId, String role, Object body)
            throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)),
                userId, role);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
