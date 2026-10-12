package com.itsm.backend.ticket.service;

import java.util.List;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketScope;
import com.itsm.backend.ticket.domain.TicketStatus;

/**
 * Ticket list filters; every field is optional.
 *
 * @param text       matched case-insensitively in the title; a number (optionally {@code #12}) also matches the id
 * @param scope      OPEN = every status except CLOSED; null means ALL
 * @param assigneeId only tickets assigned to this user (staff work queue)
 * @param unassigned only tickets nobody is assigned to (staff work queue)
 * @param sortBySla  most urgent first (see {@code TicketSpecifications}); otherwise newest first
 * @param groupIds   only tickets of these support groups (staff); null = any group
 * @param escalated  only tickets escalated because of a breached resolution SLA (staff)
 */
public record TicketSearch(String text, TicketScope scope, TicketStatus status, TicketCategory category,
        String assigneeId, boolean unassigned, boolean sortBySla, List<Long> groupIds, boolean escalated) {

    public TicketSearch(String text, TicketScope scope, TicketStatus status, TicketCategory category) {
        this(text, scope, status, category, null, false, false, null, false);
    }

    public static TicketSearch all() {
        return new TicketSearch(null, null, null, null);
    }
}
