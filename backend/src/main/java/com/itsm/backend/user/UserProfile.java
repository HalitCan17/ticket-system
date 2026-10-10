package com.itsm.backend.user;

/**
 * User attributes taken from an access token. Only {@code id} (the token subject) is required.
 *
 * @param role application role name as resolved from the token (e.g. {@code MANAGER})
 */
public record UserProfile(String id, String username, String displayName, String email, String role) {
}
