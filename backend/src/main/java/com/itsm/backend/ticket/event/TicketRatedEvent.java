package com.itsm.backend.ticket.event;

import com.itsm.backend.ticket.domain.Ticket;

/** Published inside the transaction that stored the requester's rating. */
public record TicketRatedEvent(Ticket ticket, String actorId) {
}
