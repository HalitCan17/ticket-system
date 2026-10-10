package com.itsm.backend.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    // Each test uses its own customer so the shared database does not couple tests.
    final String customer = "cust-" + UUID.randomUUID().toString().substring(0, 8);
    final String agent = "agent1";
    final String manager = "manager1";

    @Test
    void fullLifecycleWithObjection() throws Exception {
        long id = createTicket(customer);

        perform(get("/api/tickets/{id}", id), customer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.allowedTransitions", empty()));

        transition(id, agent, "AGENT", "IN_PROGRESS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assigneeId").value(agent))
                .andExpect(jsonPath("$.allowedTransitions", containsInAnyOrder("WAITING_FOR_CUSTOMER", "RESOLVED")));
        transition(id, agent, "AGENT", "WAITING_FOR_CUSTOMER", null).andExpect(status().isOk());
        transition(id, agent, "AGENT", "IN_PROGRESS", null).andExpect(status().isOk());

        transition(id, agent, "AGENT", "RESOLVED", "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESOLUTION_NOTE_REQUIRED"));
        transition(id, agent, "AGENT", "RESOLVED", "Reset the VPN profile")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionNote").value("Reset the VPN profile"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        transition(id, customer, "CUSTOMER", "IN_PROGRESS", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REOPEN_REASON_REQUIRED"));
        transition(id, customer, "CUSTOMER", "IN_PROGRESS", "Still cannot connect")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionNote").doesNotExist())
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        transition(id, agent, "AGENT", "RESOLVED", "Replaced the certificate").andExpect(status().isOk());
        transition(id, customer, "CUSTOMER", "CLOSED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        transition(id, manager, "MANAGER", "IN_PROGRESS", "reopen?")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        perform(get("/api/tickets/{id}/history", id), customer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[0].toStatus").value("NEW"))
                .andExpect(jsonPath("$[4].toStatus").value("RESOLVED"))
                .andExpect(jsonPath("$[4].note").value("Reset the VPN profile"))
                .andExpect(jsonPath("$[5].changedBy").value(customer))
                .andExpect(jsonPath("$[5].changedByName").value(TestTokens.displayName(customer)))
                .andExpect(jsonPath("$[5].note").value("Still cannot connect"))
                .andExpect(jsonPath("$[7].toStatus").value("CLOSED"));
    }

    @Test
    void responsesCarrySlaAndAutoCloseDeadline() throws Exception {
        long id = createTicket(customer); // HIGH → default target 8h

        perform(get("/api/tickets/{id}", id), customer, "CUSTOMER")
                .andExpect(jsonPath("$.sla.state").value("RUNNING"))
                .andExpect(jsonPath("$.sla.targetSeconds").value(8 * 3600))
                .andExpect(jsonPath("$.sla.breached").value(false))
                .andExpect(jsonPath("$.sla.dueAt").isString())
                .andExpect(jsonPath("$.autoCloseAt").doesNotExist());

        transition(id, agent, "AGENT", "IN_PROGRESS", null).andExpect(status().isOk());
        transition(id, agent, "AGENT", "WAITING_FOR_CUSTOMER", null)
                .andExpect(jsonPath("$.sla.state").value("PAUSED"))
                .andExpect(jsonPath("$.sla.dueAt").doesNotExist());
        transition(id, agent, "AGENT", "IN_PROGRESS", null)
                .andExpect(jsonPath("$.sla.state").value("RUNNING"));

        String resolved = transition(id, agent, "AGENT", "RESOLVED", "done")
                .andExpect(jsonPath("$.sla.state").value("STOPPED"))
                .andExpect(jsonPath("$.sla.remainingSeconds").value(
                        greaterThan(8 * 3600 - 60)))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(resolved);
        assertThat(Instant.parse(body.get("autoCloseAt").asText()))
                .isEqualTo(Instant.parse(body.get("resolvedAt").asText()).plus(Duration.ofDays(7)));

        transition(id, customer, "CUSTOMER", "CLOSED", null)
                .andExpect(jsonPath("$.autoCloseAt").doesNotExist())
                .andExpect(jsonPath("$.sla.state").value("STOPPED"));
    }

    @Test
    void createReturnsLocationAndNewTicket() throws Exception {
        perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" Printer \",\"description\":\"Jammed\",\"priority\":\"LOW\"}"),
                customer, "CUSTOMER")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/tickets/\\d+")))
                .andExpect(jsonPath("$.title").value("Printer"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.requesterId").value(customer))
                .andExpect(jsonPath("$.requesterName").value(TestTokens.displayName(customer)))
                .andExpect(jsonPath("$.assigneeName").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void customersSeeOnlyTheirOwnTickets() throws Exception {
        long id = createTicket(customer);
        String otherCustomer = customer + "-other";

        perform(get("/api/tickets/{id}", id), otherCustomer, "CUSTOMER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
        perform(get("/api/tickets/{id}/history", id), otherCustomer, "CUSTOMER")
                .andExpect(status().isNotFound());
        perform(get("/api/tickets"), otherCustomer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        perform(get("/api/tickets").param("size", "100"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.items[*].requesterId", everyItem(is(customer))))
                .andExpect(jsonPath("$.totalElements").value(1));
        perform(get("/api/tickets/{id}", id), agent, "AGENT").andExpect(status().isOk());
    }

    @Test
    void listFiltersByStatusAndPages() throws Exception {
        long first = createTicket(customer);
        long second = createTicket(customer);
        createTicket(customer);
        transition(first, agent, "AGENT", "IN_PROGRESS", null).andExpect(status().isOk());

        perform(get("/api/tickets").param("status", "NEW").param("size", "1"), customer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(not((int) first)));

        perform(get("/api/tickets").param("status", "IN_PROGRESS"), agent, "AGENT")
                .andExpect(jsonPath("$.items[*].id", hasItem((int) first)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) second))));
    }

    @Test
    void customerCannotStartWork() throws Exception {
        long id = createTicket(customer);

        transition(id, customer, "CUSTOMER", "IN_PROGRESS", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
    }

    @Test
    void requestsWithoutIdentityAreUnauthorized() throws Exception {
        mvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void invalidInputIsRejectedWithProblemDetail() throws Exception {
        perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"x\",\"priority\":\"LOW\"}"),
                customer, "CUSTOMER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"description\":\"x\",\"priority\":\"URGENT\"}"),
                customer, "CUSTOMER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        perform(get("/api/tickets").param("size", "500"), agent, "AGENT")
                .andExpect(status().isBadRequest());

        perform(get("/api/tickets/{id}", 999_999_999L), agent, "AGENT")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    private long createTicket(String requester) throws Exception {
        String body = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN down\",\"description\":\"Cannot connect\",\"priority\":\"HIGH\"}"),
                requester, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.get("id").isNumber()).isTrue();
        return node.get("id").asLong();
    }

    private ResultActions transition(long id, String userId, String role, String target, String note) throws Exception {
        String body = json.writeValueAsString(note == null
                ? Map.of("targetStatus", target)
                : Map.of("targetStatus", target, "note", note));
        return perform(post("/api/tickets/{id}/transitions", id)
                .contentType(MediaType.APPLICATION_JSON).content(body), userId, role);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
