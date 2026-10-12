package com.itsm.backend.ticket.dto;

import java.time.Instant;

import com.itsm.backend.ticket.domain.Ticket;

public record RatingResponse(int score, String comment, Instant ratedAt) {

    /** Null when the ticket has not been rated. */
    public static RatingResponse of(Ticket ticket) {
        return ticket.getRating() == null
                ? null
                : new RatingResponse(ticket.getRating(), ticket.getRatingComment(), ticket.getRatedAt());
    }
}
