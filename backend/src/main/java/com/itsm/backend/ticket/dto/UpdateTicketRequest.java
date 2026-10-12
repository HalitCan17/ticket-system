package com.itsm.backend.ticket.dto;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketPriority;
import jakarta.validation.constraints.Size;

/**
 * Staff changes to a ticket's details; omitted fields stay as they are.
 *
 * @param reason required when the priority changes
 */
public record UpdateTicketRequest(TicketPriority priority, TicketCategory category, @Size(max = 2000) String reason) {
}
