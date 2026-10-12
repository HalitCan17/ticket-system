package com.itsm.backend.user;

/** An agent or manager known to the directory (signed in at least once). */
public record StaffMember(String id, String name, String role) {
}
