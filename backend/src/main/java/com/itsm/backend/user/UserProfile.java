package com.itsm.backend.user;

/** User attributes taken from an access token. Only {@code id} (the token subject) is required. */
public record UserProfile(String id, String username, String displayName, String email) {
}
