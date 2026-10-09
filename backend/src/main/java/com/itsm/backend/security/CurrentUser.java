package com.itsm.backend.security;

import java.util.Objects;

/** The authenticated caller. {@code id} will become the Keycloak subject once Keycloak is wired in. */
public record CurrentUser(String id, Role role) {

    public CurrentUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    public boolean isStaff() {
        return role.isStaff();
    }
}
