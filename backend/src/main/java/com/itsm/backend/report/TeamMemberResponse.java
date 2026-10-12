package com.itsm.backend.report;

/**
 * Workload of one staff member.
 *
 * @param role          AGENT or MANAGER; null for an assignee the directory does not know
 * @param openWork      assigned tickets that are NEW, IN_PROGRESS or WAITING_FOR_CUSTOMER
 * @param breachedOpen  of those, past the SLA target now
 * @param resolved      tickets assigned to them that were resolved in the period
 * @param averageRating average satisfaction score of ratings given in the period; null without ratings
 */
public record TeamMemberResponse(
        String id,
        String name,
        String role,
        long openWork,
        long breachedOpen,
        long resolved,
        long ratings,
        Double averageRating) {
}
