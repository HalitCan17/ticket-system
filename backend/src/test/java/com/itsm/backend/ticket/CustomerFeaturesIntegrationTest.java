package com.itsm.backend.ticket;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import com.itsm.backend.notification.NotificationRepository;
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

/** Aşama 10: categories, search, withdrawing and rating (docs/adr/0010-customer-features.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerFeaturesIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    NotificationRepository notifications;

    // Own users per test: the database is shared with other test classes.
    final String customer = "cust-" + UUID.randomUUID().toString().substring(0, 8);
    final String otherCustomer = "cust-" + UUID.randomUUID().toString().substring(0, 8);
    final String agent = "agent-" + UUID.randomUUID().toString().substring(0, 8);
    final String tag = UUID.randomUUID().toString().substring(0, 6);

    @Test
    void categoryIsRequiredAndReturned() throws Exception {
        perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"description\":\"d\",\"priority\":\"LOW\"}"), customer, "CUSTOMER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"description\":\"d\",\"priority\":\"LOW\",\"category\":\"PRINTER\"}"),
                customer, "CUSTOMER")
                .andExpect(status().isBadRequest());

        long id = create(customer, "Yazıcı " + tag, "HARDWARE");
        perform(get("/api/tickets/{id}", id), customer, "CUSTOMER")
                .andExpect(jsonPath("$.category").value("HARDWARE"))
                .andExpect(jsonPath("$.rating").value(nullValue()))
                .andExpect(jsonPath("$.canRate").value(false));
    }

    @Test
    void searchByTitleIdScopeAndCategory() throws Exception {
        long vpn = create(customer, "VPN bağlantısı kopuyor " + tag, "NETWORK");
        long mail = create(customer, "İNTERNET ve e-posta yavaş " + tag, "SOFTWARE");
        long withdrawn = create(customer, "Fare çalışmıyor " + tag, "HARDWARE");
        transition(withdrawn, customer, "CUSTOMER", "CLOSED", null).andExpect(status().isOk());
        long foreign = create(otherCustomer, "VPN bağlantısı kopuyor " + tag, "NETWORK");

        // Case-insensitive title match, including Turkish İ; only the caller's own tickets.
        list(customer, "CUSTOMER", Map.of("q", "vpn bağlantısı"))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) vpn)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) foreign))));
        list(customer, "CUSTOMER", Map.of("q", "internet ve e-posta yavaş " + tag))
                .andExpect(jsonPath("$.items[*].id", contains((int) mail)));
        list(customer, "CUSTOMER", Map.of("q", "#" + mail))
                .andExpect(jsonPath("$.items[*].id", contains((int) mail)));
        // LIKE wildcards are literal.
        list(customer, "CUSTOMER", Map.of("q", "%" + tag))
                .andExpect(jsonPath("$.items", is(java.util.List.of())));

        list(customer, "CUSTOMER", Map.of("q", tag, "scope", "OPEN"))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) withdrawn))))
                .andExpect(jsonPath("$.items[*].status", everyItem(not("CLOSED"))));
        list(customer, "CUSTOMER", Map.of("q", tag, "scope", "CLOSED"))
                .andExpect(jsonPath("$.items[*].id", contains((int) withdrawn)));
        list(customer, "CUSTOMER", Map.of("q", tag, "category", "NETWORK"))
                .andExpect(jsonPath("$.items[*].id", contains((int) vpn)));

        // Staff search across customers.
        list(agent, "AGENT", Map.of("q", "VPN bağlantısı kopuyor " + tag))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) foreign)))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) vpn)));
    }

    @Test
    void onlyTheRequesterWithdrawsAndOnlyWhileNew() throws Exception {
        long id = create(customer, "Geri çekilecek " + tag, "OTHER");
        transition(id, agent, "AGENT", "CLOSED", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
        transition(id, otherCustomer, "CUSTOMER", "CLOSED", null).andExpect(status().isNotFound());

        transition(id, customer, "CUSTOMER", "CLOSED", "Sorun kendiliğinden düzeldi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.canRate").value(false));
        perform(get("/api/tickets/{id}/history", id), customer, "CUSTOMER")
                .andExpect(jsonPath("$[1].toStatus").value("CLOSED"))
                .andExpect(jsonPath("$[1].note").value("Sorun kendiliğinden düzeldi"));

        long started = create(customer, "İşleme alınmış " + tag, "OTHER");
        transition(started, agent, "AGENT", "IN_PROGRESS", null).andExpect(status().isOk());
        transition(started, customer, "CUSTOMER", "CLOSED", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void requesterRatesResolvedTicketOnceAndAssigneeIsNotified() throws Exception {
        long id = create(customer, "Puanlanacak " + tag, "SOFTWARE");
        rate(id, customer, 5, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_RESOLVED"));

        transition(id, agent, "AGENT", "IN_PROGRESS", null).andExpect(status().isOk());
        transition(id, agent, "AGENT", "RESOLVED", "Uygulama yeniden kuruldu").andExpect(status().isOk());

        perform(get("/api/tickets/{id}", id), customer, "CUSTOMER").andExpect(jsonPath("$.canRate").value(true));
        perform(get("/api/tickets/{id}", id), agent, "AGENT").andExpect(jsonPath("$.canRate").value(false));

        rate(id, customer, 0, null).andExpect(status().isBadRequest());
        rate(id, customer, 6, null).andExpect(status().isBadRequest());
        rate(id, otherCustomer, 5, null).andExpect(status().isNotFound());
        rate(id, agent, 5, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RATING_NOT_ALLOWED"));

        rate(id, customer, 4, " Hızlı ve net ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating.score").value(4))
                .andExpect(jsonPath("$.rating.comment").value("Hızlı ve net"))
                .andExpect(jsonPath("$.rating.ratedAt").isNotEmpty())
                .andExpect(jsonPath("$.canRate").value(false));
        rate(id, customer, 5, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RATED"));

        // Staff see the rating; the assignee got a notification, the customer did not.
        perform(get("/api/tickets/{id}", id), agent, "AGENT").andExpect(jsonPath("$.rating.score").value(4));
        perform(get("/api/notifications"), agent, "AGENT")
                .andExpect(jsonPath("$.items[0].type").value("TICKET_RATED"))
                .andExpect(jsonPath("$.items[0].ticketId").value(id));
        org.assertj.core.api.Assertions.assertThat(notifications.findByRecipientId(customer, Pageable.unpaged())
                .stream().noneMatch(n -> n.getType().name().equals("TICKET_RATED"))).isTrue();
    }

    private long create(String requester, String title, String category) throws Exception {
        String body = json.writeValueAsString(Map.of("title", title, "description", "x", "priority", "MEDIUM",
                "category", category));
        String response = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body),
                requester, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions list(String userId, String role, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/tickets").param("size", "100");
        params.forEach(request::param);
        return perform(request, userId, role).andExpect(status().isOk());
    }

    private ResultActions transition(long id, String userId, String role, String target, String note) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("targetStatus", target);
        if (note != null) {
            body.put("note", note);
        }
        return perform(post("/api/tickets/{id}/transitions", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, role);
    }

    private ResultActions rate(long id, String userId, int score, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("score", score);
        if (comment != null) {
            body.put("comment", comment);
        }
        return perform(post("/api/tickets/{id}/rating", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, role(userId));
    }

    private String role(String userId) {
        return userId.startsWith("agent") ? "AGENT" : "CUSTOMER";
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
