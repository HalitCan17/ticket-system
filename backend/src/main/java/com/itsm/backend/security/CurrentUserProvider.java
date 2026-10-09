package com.itsm.backend.security;

/** Resolves the caller of the current request (see {@link JwtCurrentUserProvider}). */
public interface CurrentUserProvider {

    /**
     * @throws com.itsm.backend.common.error.UnauthorizedException if the caller is not authenticated
     * @throws com.itsm.backend.common.error.ForbiddenException    if the caller has no application role
     */
    CurrentUser resolve();
}
