package com.itsm.backend.ticket.service;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketScope;
import com.itsm.backend.ticket.domain.TicketStatus;

/**
 * Ticket list filters; every field is optional.
 *
 * @param text  matched case-insensitively in the title; a number (optionally {@code #12}) also matches the id
 * @param scope OPEN = every status except CLOSED; null means ALL
 */
public record TicketSearch(String text, TicketScope scope, TicketStatus status, TicketCategory category) {

    public static TicketSearch all() {
        return new TicketSearch(null, null, null, null);
    }
}
