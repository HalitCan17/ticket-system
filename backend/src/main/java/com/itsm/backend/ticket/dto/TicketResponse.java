package com.itsm.backend.ticket.dto;

import java.time.Instant;
import java.util.Set;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.domain.TicketStatus;

/**
 * Built by {@link com.itsm.backend.ticket.service.TicketResponseAssembler}.
 *
 * @param requesterName      display name from the user directory; null if unknown
 * @param allowedTransitions statuses the caller may move this ticket to; lets clients show only valid actions
 * @param sla                resolution-time SLA as of the response time
 * @param autoCloseAt        when a RESOLVED ticket will be closed automatically; null in other statuses
 * @param rating             the requester's satisfaction rating; null until rated
 * @param canRate            whether the caller may rate the ticket now
 * @param canClaim           whether the caller may take the (unassigned) ticket
 * @param canReassign        whether the caller may give the ticket to someone else or unassign it
 * @param canEdit            whether the caller may change priority and category
 */
public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        Long groupId,
        String groupName,
        String requesterId,
        String requesterName,
        String assigneeId,
        String assigneeName,
        String resolutionNote,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        Set<TicketStatus> allowedTransitions,
        SlaResponse sla,
        Instant autoCloseAt,
        RatingResponse rating,
        boolean canRate,
        boolean canClaim,
        boolean canReassign,
        boolean canEdit) {
}
