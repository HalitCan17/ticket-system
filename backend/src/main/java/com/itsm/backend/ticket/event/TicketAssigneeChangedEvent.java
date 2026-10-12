package com.itsm.backend.ticket.event;

import com.itsm.backend.ticket.domain.Ticket;

/**
 * Published inside the transaction that changed the assignee.
 *
 * @param previousAssigneeId null if the ticket was unassigned
 */
public record TicketAssigneeChangedEvent(Ticket ticket, String previousAssigneeId, String actorId) {
}
