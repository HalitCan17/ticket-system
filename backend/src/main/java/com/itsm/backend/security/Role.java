package com.itsm.backend.security;

public enum Role {
    CUSTOMER,
    AGENT,
    MANAGER;

    public boolean isStaff() {
        return this == AGENT || this == MANAGER;
    }
}
