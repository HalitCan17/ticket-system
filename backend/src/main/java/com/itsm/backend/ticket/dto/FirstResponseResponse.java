package com.itsm.backend.ticket.dto;

import java.time.Duration;
import java.time.Instant;

import com.itsm.backend.ticket.domain.Ticket;

/**
 * First response SLA of a ticket (docs/adr/0015-sla-extensions.md).
 *
 * @param respondedAt      first customer-visible staff reaction; null if none yet
 * @param remainingSeconds time left while awaiting a response (negative once late); null once answered or closed
 */
public record FirstResponseResponse(Instant dueAt, Instant respondedAt, boolean breached, Long remainingSeconds) {

    /** Null for tickets without a first response target. */
    public static FirstResponseResponse of(Ticket ticket, Instant now) {
        if (ticket.getFirstResponseDueAt() == null) {
            return null;
        }
        Long remaining = ticket.isAwaitingFirstResponse()
                ? Duration.between(now, ticket.getFirstResponseDueAt()).toSeconds()
                : null;
        return new FirstResponseResponse(ticket.getFirstResponseDueAt(), ticket.getFirstRespondedAt(),
                ticket.isFirstResponseBreached(now), remaining);
    }
}
