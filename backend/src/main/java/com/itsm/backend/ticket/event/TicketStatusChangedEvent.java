package com.itsm.backend.ticket.event;

import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;

/**
 * Published inside the transaction that changed the status, so listeners writing to the database
 * commit or roll back together with the change.
 *
 * @param actorId user id, or {@link com.itsm.backend.user.UserDirectory#SYSTEM_USER_ID}
 */
public record TicketStatusChangedEvent(Ticket ticket, TicketStatus from, TicketStatus to, String actorId) {
}
