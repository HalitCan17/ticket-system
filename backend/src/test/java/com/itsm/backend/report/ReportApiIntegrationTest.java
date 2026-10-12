package com.itsm.backend.report;

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
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Aşama 13 (docs/adr/0013-manager-dashboard.md). The database is shared with other test classes, so period figures
 * are checked as differences before/after this test's own tickets; team rows use this test's own agents.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportApiIntegrationTest {

    static final CurrentUser MANAGER = new CurrentUser("manager-report", Role.MANAGER);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ReportService reports;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    final String busyAgent = "agent-busy-" + suffix;
    final String idleAgent = "agent-idle-" + suffix;

    @BeforeEach
    void signInStaff() throws Exception {
        for (String agent : List.of(busyAgent, idleAgent)) {
            perform(get("/api/notifications/unread-count"), agent, "AGENT").andExpect(status().isOk());
        }
    }

    @Test
    void onlyManagersSeeReportsAndThePeriodIsValidated() throws Exception {
        perform(get("/api/reports/summary"), "agent1", "AGENT").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_ONLY"));
        perform(get("/api/reports/team"), customer, "CUSTOMER").andExpect(status().isForbidden());
        perform(get("/api/reports/summary").param("days", "0"), "manager1", "MANAGER").andExpect(status().isBadRequest());
        perform(get("/api/reports/team").param("days", "400"), "manager1", "MANAGER").andExpect(status().isBadRequest());
        perform(get("/api/reports/summary").param("days", "7"), "manager1", "MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.statusCounts.CLOSED").isNumber())
                .andExpect(jsonPath("$.satisfaction.distribution.length()").value(5))
                .andExpect(jsonPath("$.categories.ACCESS").isNumber());
    }

    @Test
    void summaryCountsThisTestsTicketsAsDifferences() throws Exception {
        JsonNode before = summary();

        long resolvedFast = create("HIGH", "NETWORK");
        assign(resolvedFast, busyAgent);
        transition(resolvedFast, busyAgent, "AGENT", "IN_PROGRESS", null);
        transition(resolvedFast, busyAgent, "AGENT", "RESOLVED", "Tamam");
        rate(resolvedFast, 5);
        long rated2 = create("LOW", "NETWORK");
        assign(rated2, busyAgent);
        transition(rated2, busyAgent, "AGENT", "IN_PROGRESS", null);
        transition(rated2, busyAgent, "AGENT", "RESOLVED", "Tamam");
        transition(rated2, customer, "CUSTOMER", "CLOSED", null);
        rate(rated2, 2);
        long open = create("MEDIUM", "ACCESS"); // stays NEW and unassigned

        JsonNode after = summary();
        assertThat(diff(before, after, "created")).isEqualTo(3);
        assertThat(diff(before, after, "openTickets")).isEqualTo(1);
        assertThat(diff(before, after, "unassignedOpen")).isEqualTo(1);
        assertThat(diff(before, after, "categories", "NETWORK")).isEqualTo(2);
        assertThat(diff(before, after, "categories", "ACCESS")).isEqualTo(1);
        assertThat(diff(before, after, "statusCounts", "NEW")).isEqualTo(1);
        assertThat(diff(before, after, "statusCounts", "RESOLVED")).isEqualTo(1);
        assertThat(diff(before, after, "statusCounts", "CLOSED")).isEqualTo(1);
        assertThat(diff(before, after, "resolution", "resolved")).isEqualTo(2);
        assertThat(diff(before, after, "resolution", "withinSla")).isEqualTo(2); // resolved within seconds
        assertThat(diff(before, after, "satisfaction", "count")).isEqualTo(2);
        assertThat(after.at("/satisfaction/distribution/4").asLong() - before.at("/satisfaction/distribution/4").asLong())
                .isEqualTo(1);
        assertThat(after.at("/satisfaction/distribution/1").asLong() - before.at("/satisfaction/distribution/1").asLong())
                .isEqualTo(1);
        assertThat(after.at("/resolution/withinSlaRate").asDouble()).isBetween(0.0, 1.0);
        assertThat(open).isPositive();
    }

    @Test
    void breachesAreCountedAtTheReportTime() throws Exception {
        long critical = create("CRITICAL", "OTHER"); // 4 h target, clock running
        assign(critical, busyAgent);
        Instant now = Instant.now();

        long breachedNow = reports.summary(MANAGER, 30, now).breachedOpen();
        long breachedLater = reports.summary(MANAGER, 30, now.plus(Duration.ofHours(5))).breachedOpen();

        assertThat(breachedLater - breachedNow).isGreaterThanOrEqualTo(1);
        TeamMemberResponse later = reports.team(MANAGER, 30, now.plus(Duration.ofHours(5))).stream()
                .filter(member -> member.id().equals(busyAgent)).findFirst().orElseThrow();
        assertThat(later.breachedOpen()).isEqualTo(1);
    }

    @Test
    void teamShowsWorkloadPerStaffMemberBusiestFirst() throws Exception {
        long first = create("MEDIUM", "OTHER");
        long second = create("MEDIUM", "OTHER");
        long resolved = create("MEDIUM", "OTHER");
        assign(first, busyAgent);
        assign(second, busyAgent);
        assign(resolved, busyAgent);
        transition(resolved, busyAgent, "AGENT", "IN_PROGRESS", null);
        transition(resolved, busyAgent, "AGENT", "RESOLVED", "Tamam");
        rate(resolved, 4);

        JsonNode team = json.readTree(perform(get("/api/reports/team"), "manager1", "MANAGER")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode busy = member(team, busyAgent);
        assertThat(busy.get("name").asText()).isEqualTo(TestTokens.displayName(busyAgent));
        assertThat(busy.get("role").asText()).isEqualTo("AGENT");
        assertThat(busy.get("openWork").asLong()).isEqualTo(2);
        assertThat(busy.get("breachedOpen").asLong()).isZero();
        assertThat(busy.get("resolved").asLong()).isEqualTo(1);
        assertThat(busy.get("ratings").asLong()).isEqualTo(1);
        assertThat(busy.get("averageRating").asDouble()).isEqualTo(4.0);

        JsonNode idle = member(team, idleAgent);
        assertThat(idle.get("openWork").asLong()).isZero();
        assertThat(idle.get("averageRating").isNull()).isTrue();
        assertThat(index(team, busyAgent)).isLessThan(index(team, idleAgent));
    }

    private JsonNode summary() throws Exception {
        return json.readTree(perform(get("/api/reports/summary").param("days", "30"), "manager1", "MANAGER")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static long diff(JsonNode before, JsonNode after, String... path) {
        String pointer = "/" + String.join("/", path);
        return after.at(pointer).asLong() - before.at(pointer).asLong();
    }

    private static JsonNode member(JsonNode team, String id) {
        for (JsonNode member : team) {
            if (member.get("id").asText().equals(id)) {
                return member;
            }
        }
        throw new AssertionError("No team row for " + id);
    }

    private static int index(JsonNode team, String id) {
        for (int i = 0; i < team.size(); i++) {
            if (team.get(i).get("id").asText().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private long create(String priority, String category) throws Exception {
        String body = json.writeValueAsString(Map.of("title", "Rapor " + suffix, "description", "x",
                "priority", priority, "category", category));
        String response = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body),
                customer, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private void assign(long id, String assigneeId) throws Exception {
        perform(put("/api/tickets/{id}/assignee", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assigneeId", assigneeId))), "manager1", "MANAGER")
                .andExpect(status().isOk());
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

    private void rate(long id, int score) throws Exception {
        perform(post("/api/tickets/{id}/rating", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("score", score))), customer, "CUSTOMER")
                .andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
