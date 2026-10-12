package com.itsm.backend.ticket.dto;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketPriority;
import jakarta.validation.constraints.Size;

/**
 * Staff changes to a ticket's details; omitted fields stay as they are.
 *
 * @param reason      required when the priority changes
 * @param groupId     move to this support group
 * @param removeGroup take the ticket out of its group
 */
public record UpdateTicketRequest(TicketPriority priority, TicketCategory category, @Size(max = 2000) String reason,
        Long groupId, Boolean removeGroup) {

    public UpdateTicketRequest(TicketPriority priority, TicketCategory category, String reason) {
        this(priority, category, reason, null, null);
    }
}
