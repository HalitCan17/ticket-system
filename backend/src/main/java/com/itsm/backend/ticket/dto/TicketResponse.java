package com.itsm.backend.ticket.dto;

import java.time.Instant;
import java.util.Set;

import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.domain.TicketStatus;

/**
 * Built by {@link com.itsm.backend.ticket.service.TicketResponseAssembler}.
 *
 * @param allowedTransitions statuses the caller may move this ticket to; lets clients show only valid actions
 * @param sla                resolution-time SLA as of the response time
 * @param autoCloseAt        when a RESOLVED ticket will be closed automatically; null in other statuses
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
        Set<TicketStatus> allowedTransitions,
        SlaResponse sla,
        Instant autoCloseAt) {
}
