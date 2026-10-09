package com.itsm.backend.security;

import com.itsm.backend.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Secure default until Keycloak is wired in: outside the {@code dev} profile nobody is authenticated. */
@Component
@Profile("!dev")
public class UnauthenticatedCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser resolve(HttpServletRequest request) {
        throw new UnauthorizedException("Authentication is not configured.");
    }
}
