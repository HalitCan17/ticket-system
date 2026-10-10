package com.itsm.backend.attachment;

import com.itsm.backend.ticket.domain.Ticket;

/** Published inside the transaction that saved the attachment. */
public record TicketAttachmentAddedEvent(Ticket ticket, TicketAttachment attachment) {
}
