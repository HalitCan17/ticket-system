package com.itsm.backend.ticket.event;

import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketComment;

/** Published inside the transaction that saved the comment. */
public record TicketCommentAddedEvent(Ticket ticket, TicketComment comment) {
}
