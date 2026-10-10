package com.itsm.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real Keycloak (same image and realm file as infra/docker-compose.yml) issuing tokens that the
 * running backend validates: signature, issuer, audience and realm-role mapping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class KeycloakEndToEndTest {

    // Keep in sync with infra/docker-compose.yml
    static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.0";

    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_HEALTH_ENABLED", "true")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/keycloak/itsm-realm.json"),
                    "/opt/keycloak/data/import/itsm-realm.json")
            .withExposedPorts(8080, 9000)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start(); // stopped by Testcontainers' Ryuk when the JVM exits
    }

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakEndToEndTest::realmUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> realmUrl() + "/protocol/openid-connect/certs");
    }

    static String realmUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/itsm";
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    @Test
    void realTokensDriveTheTicketApi() throws Exception {
        String customer1 = token("itsm-cli", "customer1");
        String customer2 = token("itsm-cli", "customer2");
        String agent1 = token("itsm-cli", "agent1");

        ResponseEntity<String> created = call(HttpMethod.POST, "/api/tickets", customer1,
                Map.of("title", "VPN down", "description", "Cannot connect", "priority", "HIGH"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode ticket = json.readTree(created.getBody());
        assertThat(ticket.get("requesterId").asText()).isEqualTo(subjectOf(customer1));
        assertThat(ticket.get("requesterName").asText()).isEqualTo("Customer One"); // from the realm's user
        long id = ticket.get("id").asLong();

        ResponseEntity<String> started = call(HttpMethod.POST, "/api/tickets/" + id + "/transitions", agent1,
                Map.of("targetStatus", "IN_PROGRESS"));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(started.getBody()).get("assigneeId").asText()).isEqualTo(subjectOf(agent1));

        // Customer role from Keycloak is enforced: customer1 cannot start work, customer2 cannot see the ticket.
        assertThat(call(HttpMethod.POST, "/api/tickets/" + id + "/transitions", customer1,
                Map.of("targetStatus", "WAITING_FOR_CUSTOMER")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/tickets/" + id, customer2, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tokenWithoutBackendAudienceIsRejected() {
        // admin-cli exists in every realm but has no itsm-backend audience mapper.
        String foreignAudience = token("admin-cli", "customer1");

        assertThat(call(HttpMethod.GET, "/api/tickets", foreignAudience, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tamperedTokenIsRejected() {
        String[] parts = token("itsm-cli", "agent1").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"AGENT\"", "\"MANAGER\"");
        String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThat(call(HttpMethod.GET, "/api/tickets", forged, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private String token(String clientId, String username) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", username); // local development users: password = username
        JsonNode response = RestClient.create().post()
                .uri(realmUrl() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        return response.get("access_token").asText();
    }

    private String subjectOf(String token) throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
        return json.readTree(payload).get("sub").asText();
    }
}
