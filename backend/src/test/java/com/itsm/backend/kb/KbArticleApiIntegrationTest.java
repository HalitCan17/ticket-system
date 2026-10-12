package com.itsm.backend.kb;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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

/** Aşama 16 (docs/adr/0016-knowledge-base.md). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KbArticleApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /** Every title carries it, so a search for the tag sees only this test's articles in the shared database. */
    final String tag = "kb" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @Test
    void starterArticlesArePublicAndTheInternalOneIsStaffOnly() throws Exception {
        perform(get("/api/kb/articles?size=50"), "cust1", "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].title", hasItem("VPN bağlantısı kurulamıyor")))
                .andExpect(jsonPath("$.items[*].title", not(hasItem("Ağ yazıcısı sürücü kurulumu (iç not)"))));
        perform(get("/api/kb/articles?size=50"), "agent1", "AGENT")
                .andExpect(jsonPath("$.items[*].title", hasItem("Ağ yazıcısı sürücü kurulumu (iç not)")));
    }

    @Test
    void staffWriteAndOnlyManagersDelete() throws Exception {
        long id = create("agent1", "AGENT", "Yazıcı " + tag, "Kağıdı kontrol edin.", "HARDWARE", "PUBLIC");

        write(put("/api/kb/articles/{id}", id), "agent1", "AGENT",
                article("Yazıcı " + tag, "Yeni metin", "HARDWARE", "INTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Yeni metin"))
                .andExpect(jsonPath("$.visibility").value("INTERNAL"))
                .andExpect(jsonPath("$.updatedBy").value("agent1"));

        write(post("/api/kb/articles"), "cust1", "CUSTOMER", article("Müşteri " + tag, "x", "OTHER", "PUBLIC"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STAFF_ONLY"));
        perform(delete("/api/kb/articles/{id}", id), "agent1", "AGENT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_ONLY"));
        perform(delete("/api/kb/articles/{id}", id), "manager1", "MANAGER").andExpect(status().isNoContent());
        perform(get("/api/kb/articles/{id}", id), "manager1", "MANAGER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KB_ARTICLE_NOT_FOUND"));
    }

    @Test
    void customersSeeOnlyPublicArticlesAndOthersAreMissing() throws Exception {
        long open = create("agent1", "AGENT", "Açık " + tag, "herkes", "OTHER", "PUBLIC");
        long internal = create("agent1", "AGENT", "İç " + tag, "personel", "OTHER", "INTERNAL");
        long draft = create("agent1", "AGENT", "Taslak " + tag, "yazılıyor", "OTHER", "DRAFT");

        perform(get("/api/kb/articles?q=" + tag), "cust1", "CUSTOMER")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(open));
        perform(get("/api/kb/articles?q=" + tag), "agent1", "AGENT")
                .andExpect(jsonPath("$.items", hasSize(3)));
        perform(get("/api/kb/articles?q=" + tag + "&visibility=DRAFT"), "agent1", "AGENT")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(draft));
        perform(get("/api/kb/articles?q=" + tag + "&visibility=INTERNAL"), "cust1", "CUSTOMER")
                .andExpect(jsonPath("$.items", hasSize(0)));

        perform(get("/api/kb/articles/{id}", open), "cust1", "CUSTOMER").andExpect(status().isOk());
        for (long hidden : new long[] {internal, draft}) {
            perform(get("/api/kb/articles/{id}", hidden), "cust1", "CUSTOMER")
                    .andExpect(status().isNotFound());
            write(put("/api/kb/articles/{id}/vote", hidden), "cust1", "CUSTOMER", Map.of("helpful", true))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void searchMatchesTitleAndBodyIgnoringCaseAndTitleHitsComeFirst() throws Exception {
        long inBody = create("agent1", "AGENT", "Başka konu " + tag, "Burada VPN geçiyor", "NETWORK", "PUBLIC");
        long inTitle = create("agent1", "AGENT", "vpn rehberi " + tag, "ayrıntılar", "NETWORK", "PUBLIC");
        create("agent1", "AGENT", "Alakasız " + tag, "hiçbiri", "HARDWARE", "PUBLIC");

        perform(get("/api/kb/articles?q=VPN&category=NETWORK&size=50"), "cust1", "CUSTOMER")
                .andExpect(jsonPath("$.items[*].id", hasItem((int) inBody)))
                .andExpect(jsonPath("$.items[*].id", hasItem((int) inTitle)));
        String page = perform(get("/api/kb/articles?q=VPN&size=50"), "cust1", "CUSTOMER")
                .andReturn().getResponse().getContentAsString();
        var ids = json.readTree(page).get("items");
        int titlePos = -1;
        int bodyPos = -1;
        for (int i = 0; i < ids.size(); i++) {
            long value = ids.get(i).get("id").asLong();
            if (value == inTitle) {
                titlePos = i;
            } else if (value == inBody) {
                bodyPos = i;
            }
        }
        org.assertj.core.api.Assertions.assertThat(titlePos).isGreaterThanOrEqualTo(0).isLessThan(bodyPos);
    }

    @Test
    void likeWildcardsInTheQueryAreLiteral() throws Exception {
        create("agent1", "AGENT", "Yüzde 100% " + tag, "metin", "OTHER", "PUBLIC");
        create("agent1", "AGENT", "Yüzde 100 " + tag, "metin", "OTHER", "PUBLIC");

        // "100% tag" matches only the title that really contains a percent sign.
        perform(get("/api/kb/articles").param("q", "100% " + tag), "cust1", "CUSTOMER")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value("Yüzde 100% " + tag));
        // An underscore is not a one-character wildcard: "100_" + tag would match "100 " + tag otherwise.
        perform(get("/api/kb/articles").param("q", "100_ " + tag), "cust1", "CUSTOMER")
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void titlesAreUniqueIgnoringCase() throws Exception {
        long id = create("agent1", "AGENT", "Tekil " + tag, "a", "OTHER", "PUBLIC");
        write(post("/api/kb/articles"), "agent1", "AGENT", article("TEKIL " + tag.toUpperCase(), "b", "OTHER", "PUBLIC"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TITLE_TAKEN"));
        long other = create("agent1", "AGENT", "Diğer " + tag, "a", "OTHER", "PUBLIC");
        write(put("/api/kb/articles/{id}", other), "agent1", "AGENT", article("Tekil " + tag, "a", "OTHER", "PUBLIC"))
                .andExpect(status().isConflict());
        write(put("/api/kb/articles/{id}", id), "agent1", "AGENT", article("Tekil " + tag, "değişti", "OTHER", "PUBLIC"))
                .andExpect(status().isOk());
    }

    @Test
    void oneChangeableVotePerUserAndVotesGoWithTheArticle() throws Exception {
        long id = create("agent1", "AGENT", "Oy " + tag, "metin", "OTHER", "PUBLIC");

        write(put("/api/kb/articles/{id}/vote", id), "cust1", "CUSTOMER", Map.of("helpful", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulYes").value(1))
                .andExpect(jsonPath("$.helpfulNo").value(0))
                .andExpect(jsonPath("$.myVote").value(true));
        write(put("/api/kb/articles/{id}/vote", id), "cust2", "CUSTOMER", Map.of("helpful", false))
                .andExpect(jsonPath("$.helpfulYes").value(1))
                .andExpect(jsonPath("$.helpfulNo").value(1));
        write(put("/api/kb/articles/{id}/vote", id), "cust1", "CUSTOMER", Map.of("helpful", false))
                .andExpect(jsonPath("$.helpfulYes").value(0))
                .andExpect(jsonPath("$.helpfulNo").value(2))
                .andExpect(jsonPath("$.myVote").value(false));
        perform(get("/api/kb/articles/{id}", id), "agent1", "AGENT")
                .andExpect(jsonPath("$.helpfulNo").value(2))
                .andExpect(jsonPath("$.myVote").doesNotExist());
        write(put("/api/kb/articles/{id}/vote", id), "cust1", "CUSTOMER", Map.of())
                .andExpect(status().isBadRequest());

        perform(delete("/api/kb/articles/{id}", id), "manager1", "MANAGER").andExpect(status().isNoContent());
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        write(post("/api/kb/articles"), "agent1", "AGENT", article(" ", "x", "OTHER", "PUBLIC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        write(post("/api/kb/articles"), "agent1", "AGENT", article("Uzun " + tag, "x".repeat(20001), "OTHER", "PUBLIC"))
                .andExpect(status().isBadRequest());
        write(put("/api/kb/articles/{id}", 999_999), "agent1", "AGENT", article("Yok " + tag, "x", "OTHER", "PUBLIC"))
                .andExpect(status().isNotFound());
        perform(get("/api/kb/articles?size=51"), "agent1", "AGENT").andExpect(status().isBadRequest());
    }

    private long create(String userId, String role, String title, String body, String category, String visibility)
            throws Exception {
        String response = write(post("/api/kb/articles"), userId, role, article(title, body, category, visibility))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private static Map<String, Object> article(String title, String body, String category, String visibility) {
        return Map.of("title", title, "body", body, "category", category, "visibility", visibility);
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
