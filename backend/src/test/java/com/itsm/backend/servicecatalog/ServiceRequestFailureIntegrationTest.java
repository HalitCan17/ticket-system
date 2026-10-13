package com.itsm.backend.servicecatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * When a process step fails, the manager's decision must not be half applied: the request stays pending and the
 * approval task can be completed again once the problem is gone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ServiceRequestFailureIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ApprovalFlow flow;

    @MockitoSpyBean
    ServiceRequestSteps steps;

    final String tag = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "sf-cust-" + tag;
    final String manager = "sf-mgr-" + tag;

    @Test
    void aFailingFulfilmentLeavesTheRequestPendingAndRetryable() throws Exception {
        long laptop = 0;
        for (JsonNode item : read(perform(get("/api/catalog/items"), customer, "CUSTOMER"))) {
            if (item.get("name").asText().equals("Yeni dizüstü bilgisayar")) {
                laptop = item.get("id").asLong();
            }
        }
        long id = read(write(post("/api/service-requests"), customer, "CUSTOMER", Map.of("itemId", laptop,
                "answers", Map.of("model", "Standart (14 inç)", "reason", "x"))).andExpect(status().isCreated()))
                .get("id").asLong();

        doThrow(new IllegalStateException("ticket system down")).when(steps).fulfil(anyLong());
        decide(id).andExpect(status().isInternalServerError());

        perform(get("/api/service-requests/{id}", id), customer, "CUSTOMER")
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approverId").doesNotExist())
                .andExpect(jsonPath("$.ticketId").doesNotExist());
        assertThat(flow.hasApprovalTask(new CurrentUser(manager, Role.MANAGER), id)).isTrue();

        doCallRealMethod().when(steps).fulfil(anyLong());
        decide(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLING"))
                .andExpect(jsonPath("$.ticketId").isNumber());
    }

    private ResultActions decide(long id) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("approved", true);
        return write(post("/api/service-requests/{id}/decision", id), manager, "MANAGER", body);
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
