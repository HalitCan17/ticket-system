package com.itsm.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import com.itsm.backend.ticket.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Ticket actions through the API create the expected notifications in the same transaction. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationEventsIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    NotificationRepository notifications;

    static final PageRequest NEWEST_FIRST = PageRequest.of(0, 20, Sort.by("createdAt", "id").descending());

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    final String agent = "agent-" + suffix;

    @Test
    void ticketLifecycleNotifiesTheRightPeople() throws Exception {
        long id = createTicket();

        act(id, "/transitions", "{\"targetStatus\":\"IN_PROGRESS\"}", agent, "AGENT");
        act(id, "/comments", "{\"body\":\"team only\",\"visibility\":\"INTERNAL\"}", "manager-" + suffix, "MANAGER");
        act(id, "/comments", "{\"body\":\"which version?\",\"visibility\":\"EXTERNAL\"}", agent, "AGENT");
        act(id, "/comments", "{\"body\":\"Windows 11\",\"visibility\":\"EXTERNAL\"}", customer, "CUSTOMER");
        act(id, "/transitions", "{\"targetStatus\":\"RESOLVED\",\"note\":\"fixed\"}", agent, "AGENT");
        act(id, "/transitions", "{\"targetStatus\":\"IN_PROGRESS\",\"note\":\"still broken\"}", customer, "CUSTOMER");

        assertThat(typesFor(customer)).containsExactly(
                // newest first
                NotificationType.STATUS_CHANGED, // resolved
                NotificationType.COMMENT_ADDED, // "which version?"
                NotificationType.STATUS_CHANGED); // in progress
        assertThat(typesFor(agent)).containsExactly(
                NotificationType.STATUS_CHANGED, // objection
                NotificationType.COMMENT_ADDED, // "Windows 11"
                NotificationType.INTERNAL_COMMENT_ADDED);

        Notification objection = notifications.findByRecipientId(agent, NEWEST_FIRST).getContent().get(0);
        assertThat(objection.getTicketId()).isEqualTo(id);
        assertThat(objection.getActorId()).isEqualTo(customer);
        assertThat(objection.getTicketStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(objection.isRead()).isFalse();
    }

    @Test
    void rejectedActionCreatesNoNotification() throws Exception {
        long id = createTicket();

        mvc.perform(post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"RESOLVED\"}")
                        .with(TestTokens.user(customer, "CUSTOMER")))
                .andExpect(status().is4xxClientError());

        assertThat(typesFor(customer)).isEmpty();
    }

    private List<NotificationType> typesFor(String userId) {
        return notifications.findByRecipientId(userId, NEWEST_FIRST).map(Notification::getType).getContent();
    }

    private long createTicket() throws Exception {
        String body = mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN\",\"description\":\"down\",\"priority\":\"HIGH\",\"category\":\"OTHER\"}")
                        .with(TestTokens.user(customer, "CUSTOMER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void act(long id, String path, String body, String userId, String role) throws Exception {
        mvc.perform(post("/api/tickets/" + id + path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(TestTokens.user(userId, role)))
                .andExpect(status().is2xxSuccessful());
    }
}
