package com.itsm.backend.ticket.event;

import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketPriority;

/** Published inside the transaction that changed the priority. */
public record TicketPriorityChangedEvent(Ticket ticket, TicketPriority previous, String actorId) {
}
