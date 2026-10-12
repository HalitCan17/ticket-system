package com.itsm.backend.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import com.itsm.backend.ticket.service.AutoCloseScheduler;
import com.itsm.backend.ticket.service.TicketAutoCloseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Auto-close against the real database. The service takes "now" as a parameter, so instead of
 * waiting seven days the test asks it to run as if days had passed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AutoCloseIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    TicketAutoCloseService autoCloseService;

    @Autowired
    ApplicationContext context;

    final String customer = "cust-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void resolvedTicketIsClosedOnlyAfterTheWaitingPeriod() throws Exception {
        long id = createTicket();
        perform(transition(id, "{\"targetStatus\":\"IN_PROGRESS\"}"), "agent1", "AGENT");
        Instant resolvedAt = Instant.parse(json.readTree(
                perform(transition(id, "{\"targetStatus\":\"RESOLVED\",\"note\":\"fixed\"}"), "agent1", "AGENT")
                        .andReturn().getResponse().getContentAsString()).get("resolvedAt").asText());

        autoCloseService.closeStaleResolved(resolvedAt.plus(Duration.ofDays(6)));
        perform(get("/api/tickets/{id}", id), customer, "CUSTOMER")
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        autoCloseService.closeStaleResolved(resolvedAt.plus(Duration.ofDays(8)));
        perform(get("/api/tickets/{id}", id), customer, "CUSTOMER")
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").value(resolvedAt.plus(Duration.ofDays(8)).toString()))
                .andExpect(jsonPath("$.sla.state").value("STOPPED"));

        JsonNode history = json.readTree(perform(get("/api/tickets/{id}/history", id), customer, "CUSTOMER")
                .andReturn().getResponse().getContentAsString());
        JsonNode last = history.get(history.size() - 1);
        assertThat(last.get("fromStatus").asText()).isEqualTo("RESOLVED");
        assertThat(last.get("toStatus").asText()).isEqualTo("CLOSED");
        assertThat(last.get("changedBy").asText()).isEqualTo(TicketAutoCloseService.SYSTEM_ACTOR);
        assertThat(last.get("changedByName").asText()).isEqualTo("System");
        assertThat(last.get("note").asText()).isEqualTo("Auto-closed: no customer response for 7d");
    }

    @Test
    void objectedTicketIsNotClosed() throws Exception {
        long id = createTicket();
        perform(transition(id, "{\"targetStatus\":\"IN_PROGRESS\"}"), "agent1", "AGENT");
        perform(transition(id, "{\"targetStatus\":\"RESOLVED\",\"note\":\"fixed\"}"), "agent1", "AGENT");
        perform(transition(id, "{\"targetStatus\":\"IN_PROGRESS\",\"note\":\"still broken\"}"), customer, "CUSTOMER");

        autoCloseService.closeStaleResolved(Instant.now().plus(Duration.ofDays(30)));

        perform(get("/api/tickets/{id}", id), customer, "CUSTOMER")
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void schedulerIsEnabledByDefault() {
        assertThat(context.getBeansOfType(AutoCloseScheduler.class)).hasSize(1);
    }

    private long createTicket() throws Exception {
        String body = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Printer\",\"description\":\"Jammed\",\"priority\":\"LOW\",\"category\":\"OTHER\"}"),
                customer, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private static MockHttpServletRequestBuilder transition(long id, String body) {
        return post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role))).andExpect(status().is2xxSuccessful());
    }
}
