package com.itsm.backend.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller of the current request. Exactly one implementation is active per profile:
 * {@link DevHeaderCurrentUserProvider} in {@code dev}, {@link UnauthenticatedCurrentUserProvider} otherwise.
 * The Keycloak stage replaces both with a JWT-based implementation.
 */
public interface CurrentUserProvider {

    /** @throws com.itsm.backend.common.error.UnauthorizedException if the caller cannot be identified */
    CurrentUser resolve(HttpServletRequest request);
}
