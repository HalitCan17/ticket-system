package com.itsm.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc authentication shaped like a Keycloak access token (subject + realm roles). */
public final class TestTokens {

    private TestTokens() {
    }

    public static RequestPostProcessor user(String subject, String role) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(role))));
    }
}
