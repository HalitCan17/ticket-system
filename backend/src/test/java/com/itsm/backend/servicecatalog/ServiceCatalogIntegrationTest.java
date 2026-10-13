package com.itsm.backend.servicecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Aşama 17 (docs/adr/0017-service-catalog.md): catalog, requests and the jBPM approval process. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ServiceCatalogIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ApprovalFlow flow;

    @Autowired
    JdbcTemplate jdbc;

    final String tag = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "sc-cust-" + tag;
    final String otherCustomer = "sc-other-" + tag;
    final String agent = "sc-agent-" + tag;
    final String manager = "sc-mgr-" + tag;

    long laptop;
    long vpn;

    @BeforeEach
    void findStarterItems() throws Exception {
        // Signing in once makes the manager known to the user directory (approval notifications go to them).
        JsonNode items = read(perform(get("/api/catalog/items"), manager, "MANAGER").andExpect(status().isOk()));
        for (JsonNode item : items) {
            switch (item.get("name").asText()) {
                case "Yeni dizüstü bilgisayar" -> laptop = item.get("id").asLong();
                case "VPN erişimi" -> vpn = item.get("id").asLong();
                default -> { }
            }
        }
        assertThat(laptop).isPositive();
        assertThat(vpn).isPositive();
    }

    @Test
    void approvedRequestBecomesATicketForTheRequester() throws Exception {
        long id = request(customer, laptop, Map.of("model", "Standart (14 inç)", "reason", " Eskisi bozuldu "))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.answers.reason").value("Eskisi bozuldu"))
                .andExpect(jsonPath("$.ticketId").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(this::id);

        // The approval task belongs to managers only, and is stored by the engine in Postgres.
        assertThat(flow.hasApprovalTask(new CurrentUser(manager, Role.MANAGER), id)).isTrue();
        assertThat(flow.hasApprovalTask(new CurrentUser(agent, Role.AGENT), id)).isFalse();
        String processId = jdbc.queryForObject("select process_instance_id from service_requests where id = ?",
                String.class, id);
        assertThat(jdbc.queryForObject("select count(*) from process_instances where id = ?", Integer.class,
                processId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from jbpm_user_tasks where status = 'Ready' and"
                + " process_instance_id = ?", Integer.class, processId)).isEqualTo(1);

        perform(get("/api/service-requests?status=PENDING_APPROVAL&size=100"), manager, "MANAGER")
                .andExpect(jsonPath("$.items[*].id", hasItem((int) id)));
        perform(get("/api/service-requests/{id}", id), manager, "MANAGER").andExpect(jsonPath("$.canDecide").value(true));
        decide(id, agent, "AGENT", true, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_ONLY"));

        JsonNode decided = read(decide(id, manager, "MANAGER", true, "Bütçe uygun").andExpect(status().isOk()));
        assertThat(decided.get("status").asText()).isEqualTo("FULFILLING");
        assertThat(decided.get("approverId").asText()).isEqualTo(manager);
        long ticketId = decided.get("ticketId").asLong();
        assertThat(ticketId).isPositive();
        assertThat(flow.isActive(processId)).isFalse();

        perform(get("/api/tickets/{id}", ticketId), customer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Yeni dizüstü bilgisayar"))
                .andExpect(jsonPath("$.category").value("HARDWARE"))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString(
                        "Model: Standart (14 inç)\nGerekçe: Eskisi bozuldu\nOnay notu: Bütçe uygun")))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString(
                        "Hizmet talebi #" + id)));

        perform(get("/api/notifications"), manager, "MANAGER")
                .andExpect(jsonPath("$.items[?(@.serviceRequestId == " + id + ")].type", hasItem("APPROVAL_REQUESTED")));
        perform(get("/api/notifications"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.items[?(@.serviceRequestId == " + id + ")].type", hasItem("REQUEST_APPROVED")))
                .andExpect(jsonPath("$.items[?(@.serviceRequestId == " + id + ")].serviceRequestTitle",
                        hasItem("Yeni dizüstü bilgisayar")));

        decide(id, manager, "MANAGER", true, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PENDING"));
    }

    @Test
    void rejectionNeedsAReasonAndOpensNoTicket() throws Exception {
        long id = request(customer, laptop, Map.of("model", "Standart (14 inç)", "reason", "Yedek"))
                .andReturn().getResponse().getContentAsString().transform(this::id);

        decide(id, manager, "MANAGER", false, " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        decide(id, manager, "MANAGER", false, "Bütçe yok")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionComment").value("Bütçe yok"))
                .andExpect(jsonPath("$.ticketId").doesNotExist());

        perform(get("/api/notifications"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.items[?(@.serviceRequestId == " + id + ")].type", hasItem("REQUEST_REJECTED")));
    }

    @Test
    void requestWithoutApprovalIsFulfilledAtOnce() throws Exception {
        JsonNode created = read(request(customer, vpn, Map.of("device", "Telefon")));
        assertThat(created.get("status").asText()).isEqualTo("FULFILLING");
        long ticketId = created.get("ticketId").asLong();
        perform(get("/api/tickets/{id}", ticketId), customer, "CUSTOMER")
                .andExpect(jsonPath("$.category").value("NETWORK"))
                .andExpect(jsonPath("$.requesterId").value(customer));
        perform(get("/api/notifications"), manager, "MANAGER")
                .andExpect(jsonPath("$.items[*].serviceRequestId", not(hasItem(created.get("id").asInt()))));
    }

    @Test
    void onlyTheRequesterWithdrawsAPendingRequestAndTheTaskGoesAway() throws Exception {
        long id = request(customer, laptop, Map.of("model", "Standart (14 inç)", "reason", "x"))
                .andReturn().getResponse().getContentAsString().transform(this::id);

        perform(get("/api/service-requests/{id}", id), otherCustomer, "CUSTOMER").andExpect(status().isNotFound());
        perform(post("/api/service-requests/{id}/cancel", id), otherCustomer, "CUSTOMER")
                .andExpect(status().isNotFound());
        perform(post("/api/service-requests/{id}/cancel", id), agent, "AGENT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_REQUESTER"));
        perform(post("/api/service-requests/{id}/cancel", id), customer, "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.canCancel").value(false));

        assertThat(flow.hasApprovalTask(new CurrentUser(manager, Role.MANAGER), id)).isFalse();
        decide(id, manager, "MANAGER", true, null).andExpect(status().isConflict());
        perform(post("/api/service-requests/{id}/cancel", id), customer, "CUSTOMER").andExpect(status().isConflict());
    }

    @Test
    void managersDoNotDecideTheirOwnRequests() throws Exception {
        long id = request(manager, laptop, Map.of("model", "Standart (14 inç)", "reason", "x"), "MANAGER")
                .andReturn().getResponse().getContentAsString().transform(this::id);
        perform(get("/api/service-requests/{id}", id), manager, "MANAGER").andExpect(jsonPath("$.canDecide").value(false));
        decide(id, manager, "MANAGER", true, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL"));
        perform(post("/api/service-requests/{id}/cancel", id), manager, "MANAGER").andExpect(status().isOk());
    }

    @Test
    void customersListOnlyTheirOwnRequests() throws Exception {
        long mine = read(request(customer, vpn, Map.of("device", "Dizüstü"))).get("id").asLong();
        long theirs = read(request(otherCustomer, vpn, Map.of("device", "Dizüstü"))).get("id").asLong();

        perform(get("/api/service-requests?size=100"), customer, "CUSTOMER")
                .andExpect(jsonPath("$.items[*].id", hasItem((int) mine)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) theirs))));
        perform(get("/api/service-requests?size=100"), agent, "AGENT")
                .andExpect(jsonPath("$.items[*].id", hasItem((int) theirs)));
        perform(get("/api/service-requests?mine=true&size=100"), agent, "AGENT")
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) theirs))));
    }

    @Test
    void answersAreCheckedAgainstTheItemsForm() throws Exception {
        write(post("/api/service-requests"), customer, "CUSTOMER",
                Map.of("itemId", laptop, "answers", Map.of("model", "Süper bilgisayar")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ANSWERS"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("answers.model")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("answers.reason")));
        write(post("/api/service-requests"), customer, "CUSTOMER", Map.of("itemId", 999_999, "answers", Map.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_NOT_FOUND"));
    }

    @Test
    void managersMaintainTheCatalogAndInactiveItemsAreHiddenFromCustomers() throws Exception {
        Map<String, Object> item = Map.of("name", "Monitör " + tag, "description", "İkinci ekran",
                "category", "HARDWARE", "priority", "LOW", "requiresApproval", false, "active", true,
                "fields", List.of(Map.of("key", "size", "label", "Boyut", "type", "SELECT",
                        "options", List.of("24", "27"), "required", true)));
        write(post("/api/catalog/items"), agent, "AGENT", item).andExpect(status().isForbidden());
        long id = read(write(post("/api/catalog/items"), manager, "MANAGER", item).andExpect(status().isCreated()))
                .get("id").asLong();
        write(post("/api/catalog/items"), manager, "MANAGER", item).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_TAKEN"));

        var broken = new java.util.HashMap<>(item);
        broken.put("name", "Bozuk " + tag);
        broken.put("fields", List.of(Map.of("key", "size", "label", "Boyut", "type", "SELECT", "required", true)));
        write(post("/api/catalog/items"), manager, "MANAGER", broken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FORM"))
                .andExpect(jsonPath("$.errors[0].field").value("fields[0].options"));

        var inactive = new java.util.HashMap<>(item);
        inactive.put("active", false);
        write(put("/api/catalog/items/{id}", id), manager, "MANAGER", inactive).andExpect(status().isOk());
        perform(get("/api/catalog/items"), customer, "CUSTOMER")
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        perform(get("/api/catalog/items/{id}", id), customer, "CUSTOMER").andExpect(status().isNotFound());
        write(post("/api/service-requests"), customer, "CUSTOMER", Map.of("itemId", id, "answers", Map.of("size", "24")))
                .andExpect(status().isNotFound());
        write(post("/api/service-requests"), agent, "AGENT", Map.of("itemId", id, "answers", Map.of("size", "24")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ITEM_INACTIVE"));
    }

    private ResultActions request(String userId, long itemId, Map<String, String> answers) throws Exception {
        return request(userId, itemId, answers, "CUSTOMER");
    }

    private ResultActions request(String userId, long itemId, Map<String, String> answers, String role)
            throws Exception {
        return write(post("/api/service-requests"), userId, role, Map.of("itemId", itemId, "answers", answers))
                .andExpect(status().isCreated());
    }

    private ResultActions decide(long id, String userId, String role, boolean approved, String comment)
            throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("approved", approved);
        body.put("comment", comment);
        return write(post("/api/service-requests/{id}/decision", id), userId, role, body);
    }

    private long id(String body) {
        try {
            return json.readTree(body).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
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
