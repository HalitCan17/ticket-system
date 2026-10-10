package com.itsm.backend.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AttachmentApiIntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] LOG = "2026-10-11 ERROR Outlook crashed\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AttachmentProperties properties;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String customer = "cust-" + suffix;
    final String agent = "agent-" + suffix;
    final String manager = "manager-" + suffix;

    @Test
    void uploadListAndDownload() throws Exception {
        long ticketId = createTicket();

        long id = upload(ticketId, "ekran görüntüsü.png", PNG, "EXTERNAL", customer, "CUSTOMER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("ekran görüntüsü.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(PNG.length))
                .andExpect(jsonPath("$.uploaderName").value(TestTokens.displayName(customer)))
                .andExpect(jsonPath("$.canDelete").value(true))
                .andReturn().getResponse().getContentAsString().transform(this::id);

        perform(get("/api/tickets/{t}/attachments", ticketId), agent, "AGENT")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canDelete").value(false));

        byte[] downloaded = perform(get("/api/tickets/{t}/attachments/{a}/content", ticketId, id), agent, "AGENT")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''ekran%20g%C3%B6r%C3%BCnt%C3%BCs%C3%BC.png")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PNG);

        String key = jdbc.queryForObject("select storage_key from ticket_attachments where id = ?", String.class, id);
        assertThat(properties.dir().resolve(key)).exists();
    }

    @Test
    void uploadNotifiesTheOtherParticipants() throws Exception {
        long ticketId = createTicket();
        transition(ticketId, "{\"targetStatus\":\"IN_PROGRESS\"}", agent, "AGENT");

        upload(ticketId, "log.txt", LOG, "EXTERNAL", customer, "CUSTOMER").andExpect(status().isCreated());
        upload(ticketId, "internal.txt", LOG, "INTERNAL", manager, "MANAGER").andExpect(status().isCreated());

        assertThat(jdbc.queryForList(
                "select type from notifications where recipient_id = ? and ticket_id = ? order by id",
                String.class, agent, ticketId)).containsExactly("ATTACHMENT_ADDED", "ATTACHMENT_ADDED");
        assertThat(jdbc.queryForList(
                "select type from notifications where recipient_id = ? and ticket_id = ? and type = 'ATTACHMENT_ADDED'",
                String.class, customer, ticketId)).isEmpty(); // own upload; internal one is hidden
    }

    @Test
    void internalAttachmentsAreInvisibleToTheCustomer() throws Exception {
        long ticketId = createTicket();
        long id = upload(ticketId, "notes.log", LOG, "INTERNAL", agent, "AGENT")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::id);

        perform(get("/api/tickets/{t}/attachments", ticketId), customer, "CUSTOMER")
                .andExpect(jsonPath("$.length()").value(0));
        perform(get("/api/tickets/{t}/attachments/{a}/content", ticketId, id), customer, "CUSTOMER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
        upload(ticketId, "x.log", LOG, "INTERNAL", customer, "CUSTOMER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERNAL_ATTACHMENT_NOT_ALLOWED"));
    }

    @Test
    void otherCustomersCannotSeeOrAdd() throws Exception {
        long ticketId = createTicket();
        long id = upload(ticketId, "a.png", PNG, "EXTERNAL", customer, "CUSTOMER")
                .andReturn().getResponse().getContentAsString().transform(this::id);
        String other = "other-" + suffix;

        perform(get("/api/tickets/{t}/attachments/{a}/content", ticketId, id), other, "CUSTOMER")
                .andExpect(status().isNotFound());
        upload(ticketId, "b.png", PNG, "EXTERNAL", other, "CUSTOMER").andExpect(status().isNotFound());
    }

    @Test
    void attachmentOfAnotherTicketIsNotFound() throws Exception {
        long first = createTicket();
        long second = createTicket();
        long id = upload(first, "a.png", PNG, "EXTERNAL", customer, "CUSTOMER")
                .andReturn().getResponse().getContentAsString().transform(this::id);

        perform(get("/api/tickets/{t}/attachments/{a}/content", second, id), customer, "CUSTOMER")
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRules() throws Exception {
        long ticketId = createTicket();
        long byCustomer = upload(ticketId, "a.png", PNG, "EXTERNAL", customer, "CUSTOMER")
                .andReturn().getResponse().getContentAsString().transform(this::id);
        long byAgent = upload(ticketId, "b.log", LOG, "EXTERNAL", agent, "AGENT")
                .andReturn().getResponse().getContentAsString().transform(this::id);
        String key = jdbc.queryForObject("select storage_key from ticket_attachments where id = ?", String.class, byCustomer);

        perform(delete("/api/tickets/{t}/attachments/{a}", ticketId, byAgent), customer, "CUSTOMER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_DELETE_NOT_ALLOWED"));
        perform(delete("/api/tickets/{t}/attachments/{a}", ticketId, byCustomer), customer, "CUSTOMER")
                .andExpect(status().isNoContent());
        perform(delete("/api/tickets/{t}/attachments/{a}", ticketId, byAgent), manager, "MANAGER")
                .andExpect(status().isNoContent());

        perform(get("/api/tickets/{t}/attachments", ticketId), agent, "AGENT").andExpect(jsonPath("$.length()").value(0));
        assertThat(properties.dir().resolve(key)).doesNotExist();
    }

    @Test
    void closedTicketTakesNoChanges() throws Exception {
        long ticketId = createTicket();
        long id = upload(ticketId, "a.png", PNG, "EXTERNAL", customer, "CUSTOMER")
                .andReturn().getResponse().getContentAsString().transform(this::id);
        transition(ticketId, "{\"targetStatus\":\"IN_PROGRESS\"}", agent, "AGENT");
        transition(ticketId, "{\"targetStatus\":\"RESOLVED\",\"note\":\"done\"}", agent, "AGENT");
        transition(ticketId, "{\"targetStatus\":\"CLOSED\"}", customer, "CUSTOMER");

        upload(ticketId, "b.png", PNG, "EXTERNAL", customer, "CUSTOMER")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TICKET_CLOSED"));
        perform(delete("/api/tickets/{t}/attachments/{a}", ticketId, id), customer, "CUSTOMER")
                .andExpect(status().isConflict());
        perform(get("/api/tickets/{t}/attachments", ticketId), customer, "CUSTOMER")
                .andExpect(jsonPath("$[0].canDelete").value(false));
    }

    @Test
    void rejectsUnsafeTooLargeAndEmptyFiles() throws Exception {
        long ticketId = createTicket();
        long filesBefore = countFiles();

        upload(ticketId, "setup.exe", new byte[] {'M', 'Z', 0, 0}, "EXTERNAL", customer, "CUSTOMER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload(ticketId, "fake.png", new byte[] {'M', 'Z', 0, 0}, "EXTERNAL", customer, "CUSTOMER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload(ticketId, "empty.txt", new byte[0], "EXTERNAL", customer, "CUSTOMER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));

        byte[] tooLarge = new byte[(int) properties.maxFileSize().toBytes() + 1];
        Arrays.fill(tooLarge, (byte) 'a');
        upload(ticketId, "big.txt", tooLarge, "EXTERNAL", customer, "CUSTOMER")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        perform(get("/api/tickets/{t}/attachments", ticketId), customer, "CUSTOMER")
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(countFiles()).isEqualTo(filesBefore); // nothing written for rejected uploads
    }

    private long countFiles() throws Exception {
        try (var files = Files.list(properties.dir())) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private long id(String body) {
        try {
            return json.readTree(body).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions upload(long ticketId, String name, byte[] content, String visibility, String userId,
            String role) throws Exception {
        return mvc.perform(multipart("/api/tickets/{t}/attachments", ticketId)
                .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .param("visibility", visibility)
                .with(TestTokens.user(userId, role)));
    }

    private long createTicket() throws Exception {
        String body = perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN\",\"description\":\"down\",\"priority\":\"HIGH\"}"), customer, "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(body);
    }

    private void transition(long ticketId, String body, String userId, String role) throws Exception {
        perform(post("/api/tickets/{t}/transitions", ticketId).contentType(MediaType.APPLICATION_JSON).content(body),
                userId, role).andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
