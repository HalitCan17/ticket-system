package com.itsm.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc authentication shaped like a Keycloak access token (subject, realm roles, name claims). */
public final class TestTokens {

    private TestTokens() {
    }

    public static RequestPostProcessor user(String subject, String role) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("preferred_username", subject)
                .claim("name", displayName(subject))
                .claim("realm_access", Map.of("roles", List.of(role))));
    }

    /** The {@code name} claim {@link #user} puts into the token. */
    public static String displayName(String subject) {
        return "Name of " + subject;
    }
}
