package com.itsm.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApplicationSmokeTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void ticketApiRejectsRequestsOutsideDevProfile() {
        // Even with the dev headers, no identity is accepted without the `dev` profile.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "agent1");
        headers.set("X-User-Role", "AGENT");

        ResponseEntity<String> response = rest.exchange(
                "/api/tickets", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void flywayBaselineMigrationApplied() {
        Boolean success = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }
}
