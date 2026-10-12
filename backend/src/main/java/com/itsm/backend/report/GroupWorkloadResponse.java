package com.itsm.backend.report;

/**
 * Open work of one support group, as of the report time.
 *
 * @param id      null for tickets that belong to no group
 * @param members number of members
 */
public record GroupWorkloadResponse(Long id, String name, long members, long openWork, long unassignedOpen,
        long breachedOpen) {
}
