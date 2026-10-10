package com.itsm.backend.notification;

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
class NotificationApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    final String agent = "agent-" + suffix;

    @Test
    void listsOwnNotificationsWithTitleAndActorName() throws Exception {
        long id = createTicket("Yazıcı");
        perform(post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"IN_PROGRESS\"}"), agent, "AGENT");

        perform(get("/api/notifications"), customer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[0].ticketId").value(id))
                .andExpect(jsonPath("$.items[0].ticketTitle").value("Yazıcı"))
                .andExpect(jsonPath("$.items[0].ticketStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].actorId").value(agent))
                .andExpect(jsonPath("$.items[0].actorName").value(TestTokens.displayName(agent)))
                .andExpect(jsonPath("$.items[0].read").value(false));

        // The agent caused it and gets nothing.
        perform(get("/api/notifications/unread-count"), agent, "AGENT")
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void markingReadOneOrAll() throws Exception {
        long first = createTicket("Bir");
        long second = createTicket("İki");
        start(first);
        start(second);

        perform(get("/api/notifications/unread-count"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.count").value(2));

        long notificationId = json.readTree(perform(get("/api/notifications"), customer, "CUSTOMER")
                .andReturn().getResponse().getContentAsString()).get("items").get(0).get("id").asLong();
        perform(post("/api/notifications/{id}/read", notificationId), customer, "CUSTOMER")
                .andExpect(status().isNoContent());
        perform(post("/api/notifications/{id}/read", notificationId), customer, "CUSTOMER")
                .andExpect(status().isNoContent()); // idempotent

        perform(get("/api/notifications").param("unreadOnly", "true"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].read").value(false));

        perform(post("/api/notifications/read-all"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.updated").value(1));
        perform(get("/api/notifications/unread-count"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void someoneElsesNotificationIsNotFound() throws Exception {
        start(createTicket("Gizli"));
        long notificationId = json.readTree(perform(get("/api/notifications"), customer, "CUSTOMER")
                .andReturn().getResponse().getContentAsString()).get("items").get(0).get("id").asLong();

        perform(post("/api/notifications/{id}/read", notificationId), "other-" + suffix, "CUSTOMER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        perform(get("/api/notifications"), "other-" + suffix, "CUSTOMER")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void requiresAuthenticationAndValidPaging() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        perform(get("/api/notifications").param("size", "500"), customer, "CUSTOMER")
                .andExpect(status().isBadRequest());
    }

    private long createTicket(String title) throws Exception {
        String body = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "title", title, "description", "x", "priority", "LOW"))),
                customer, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void start(long ticketId) throws Exception {
        perform(post("/api/tickets/{id}/transitions", ticketId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"IN_PROGRESS\"}"), agent, "AGENT")
                .andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
