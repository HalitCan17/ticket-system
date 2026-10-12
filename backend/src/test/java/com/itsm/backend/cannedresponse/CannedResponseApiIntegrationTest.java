package com.itsm.backend.cannedresponse;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Aşama 12 (docs/adr/0012-canned-responses.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CannedResponseApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    final String tag = UUID.randomUUID().toString().substring(0, 8);

    @Test
    void staffListTemplatesIncludingTheStarterSet() throws Exception {
        perform(get("/api/canned-responses"), "agent1", "AGENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Ek bilgi isteği")))
                .andExpect(jsonPath("$[?(@.title == 'Üçüncü taraf bekleniyor')].visibility", hasItem("INTERNAL")));
        perform(get("/api/canned-responses"), "cust1", "CUSTOMER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ONLY"));
    }

    @Test
    void managerCreatesUpdatesAndDeletes() throws Exception {
        String body = write(post("/api/canned-responses"), "manager1", "MANAGER",
                Map.of("title", " Şifre sıfırlama " + tag, "body", " Merhaba {müşteri}, şifreniz sıfırlandı. ",
                        "visibility", "EXTERNAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Şifre sıfırlama " + tag))
                .andExpect(jsonPath("$.body").value("Merhaba {müşteri}, şifreniz sıfırlandı."))
                .andExpect(jsonPath("$.updatedBy").value("manager1"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        write(put("/api/canned-responses/{id}", id), "manager1", "MANAGER",
                Map.of("title", "Şifre sıfırlama " + tag, "body", "Yeni metin", "visibility", "INTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Yeni metin"))
                .andExpect(jsonPath("$.visibility").value("INTERNAL"));

        perform(delete("/api/canned-responses/{id}", id), "manager1", "MANAGER").andExpect(status().isNoContent());
        perform(get("/api/canned-responses"), "agent1", "AGENT")
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        perform(delete("/api/canned-responses/{id}", id), "manager1", "MANAGER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CANNED_RESPONSE_NOT_FOUND"));
    }

    @Test
    void onlyManagersChangeTemplates() throws Exception {
        Map<String, String> template = Map.of("title", "Agent " + tag, "body", "x", "visibility", "EXTERNAL");
        write(post("/api/canned-responses"), "agent1", "AGENT", template)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_ONLY"));
        write(post("/api/canned-responses"), "cust1", "CUSTOMER", template).andExpect(status().isForbidden());
        perform(delete("/api/canned-responses/{id}", 1), "agent1", "AGENT").andExpect(status().isForbidden());
    }

    @Test
    void titlesAreUniqueIgnoringCase() throws Exception {
        write(post("/api/canned-responses"), "manager1", "MANAGER",
                Map.of("title", "VPN kurulumu " + tag, "body", "a", "visibility", "EXTERNAL"))
                .andExpect(status().isCreated());
        String other = write(post("/api/canned-responses"), "manager1", "MANAGER",
                Map.of("title", "Başka " + tag, "body", "b", "visibility", "EXTERNAL"))
                .andReturn().getResponse().getContentAsString();

        write(post("/api/canned-responses"), "manager1", "MANAGER",
                Map.of("title", "vpn KURULUMU " + tag, "body", "c", "visibility", "EXTERNAL"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TITLE_TAKEN"));
        write(put("/api/canned-responses/{id}", json.readTree(other).get("id").asLong()), "manager1", "MANAGER",
                Map.of("title", "VPN Kurulumu " + tag, "body", "b", "visibility", "EXTERNAL"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        write(post("/api/canned-responses"), "manager1", "MANAGER",
                Map.of("title", " ", "body", "x", "visibility", "EXTERNAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        write(post("/api/canned-responses"), "manager1", "MANAGER",
                Map.of("title", "Uzun " + tag, "body", "x".repeat(5001), "visibility", "EXTERNAL"))
                .andExpect(status().isBadRequest());
        write(put("/api/canned-responses/{id}", 999_999), "manager1", "MANAGER",
                Map.of("title", "Yok " + tag, "body", "x", "visibility", "EXTERNAL"))
                .andExpect(status().isNotFound());
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
