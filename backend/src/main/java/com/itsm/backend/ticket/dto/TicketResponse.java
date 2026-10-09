package com.itsm.backend.ticket.dto;

import java.time.Instant;
import java.util.Set;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketWorkflow;

/**
 * @param allowedTransitions statuses the caller may move this ticket to; lets clients show only valid actions
 */
public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        String requesterId,
        String assigneeId,
        String resolutionNote,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        Set<TicketStatus> allowedTransitions) {

    public static TicketResponse from(Ticket ticket, CurrentUser viewer) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getRequesterId(),
                ticket.getAssigneeId(),
                ticket.getResolutionNote(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                TicketWorkflow.allowedTargets(ticket.getStatus(), viewer, ticket.getRequesterId()));
    }
}
