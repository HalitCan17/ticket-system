package com.itsm.backend.ticket;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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
class TicketCommentApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    final String customer = "cust-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void internalCommentsAreHiddenFromCustomer() throws Exception {
        long id = createTicket();

        comment(id, "agent1", "AGENT", "Looks like a cert issue", "INTERNAL").andExpect(status().isCreated());
        comment(id, "agent1", "AGENT", "Which OS are you on?", "EXTERNAL")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value("agent1"))
                .andExpect(jsonPath("$.authorName").value(TestTokens.displayName("agent1")))
                .andExpect(jsonPath("$.visibility").value("EXTERNAL"))
                .andExpect(jsonPath("$.id").isNumber());
        comment(id, customer, "CUSTOMER", "Windows 11", "EXTERNAL").andExpect(status().isCreated());

        perform(get("/api/tickets/{id}/comments", id), customer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].body", contains("Which OS are you on?", "Windows 11")));

        perform(get("/api/tickets/{id}/comments", id), "manager1", "MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].body",
                        contains("Looks like a cert issue", "Which OS are you on?", "Windows 11")));
    }

    @Test
    void customerCannotPostInternalComment() throws Exception {
        long id = createTicket();

        comment(id, customer, "CUSTOMER", "psst", "INTERNAL")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERNAL_COMMENT_NOT_ALLOWED"));
    }

    @Test
    void otherCustomerCannotReadOrWriteComments() throws Exception {
        long id = createTicket();

        perform(get("/api/tickets/{id}/comments", id), customer + "-x", "CUSTOMER")
                .andExpect(status().isNotFound());
        comment(id, customer + "-x", "CUSTOMER", "hello", "EXTERNAL")
                .andExpect(status().isNotFound());
    }

    @Test
    void closedTicketRejectsComments() throws Exception {
        long id = createTicket();
        transition(id, "agent1", "AGENT", "{\"targetStatus\":\"IN_PROGRESS\"}");
        transition(id, "agent1", "AGENT", "{\"targetStatus\":\"RESOLVED\",\"note\":\"done\"}");
        transition(id, customer, "CUSTOMER", "{\"targetStatus\":\"CLOSED\"}");

        comment(id, customer, "CUSTOMER", "thanks", "EXTERNAL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_CLOSED"));
    }

    @Test
    void blankCommentIsInvalid() throws Exception {
        long id = createTicket();

        comment(id, customer, "CUSTOMER", " ", "EXTERNAL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private long createTicket() throws Exception {
        String body = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN down\",\"description\":\"Cannot connect\",\"priority\":\"HIGH\"}"),
                customer, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void transition(long id, String userId, String role, String body) throws Exception {
        perform(post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON).content(body),
                userId, role)
                .andExpect(status().isOk());
    }

    private ResultActions comment(long id, String userId, String role, String body, String visibility) throws Exception {
        String payload = json.writeValueAsString(Map.of("body", body, "visibility", visibility));
        return perform(post("/api/tickets/{id}/comments", id).contentType(MediaType.APPLICATION_JSON).content(payload),
                userId, role);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
