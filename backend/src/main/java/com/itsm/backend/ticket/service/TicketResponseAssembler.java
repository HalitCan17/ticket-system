package com.itsm.backend.ticket.service;

import java.time.Clock;
import java.time.Instant;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketWorkflow;
import com.itsm.backend.ticket.dto.SlaResponse;
import com.itsm.backend.ticket.dto.TicketResponse;
import org.springframework.stereotype.Component;

/** Maps a ticket to its API shape; needs the clock (SLA "now") and the auto-close policy. */
@Component
public class TicketResponseAssembler {

    private final Clock clock;
    private final AutoCloseProperties autoClose;

    public TicketResponseAssembler(Clock clock, AutoCloseProperties autoClose) {
        this.clock = clock;
        this.autoClose = autoClose;
    }

    public TicketResponse toResponse(Ticket ticket, CurrentUser viewer) {
        Instant now = clock.instant();
        Instant autoCloseAt = ticket.getStatus() == TicketStatus.RESOLVED && ticket.getResolvedAt() != null
                ? ticket.getResolvedAt().plus(autoClose.after())
                : null;
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
                TicketWorkflow.allowedTargets(ticket.getStatus(), viewer, ticket.getRequesterId()),
                SlaResponse.of(ticket.getSlaState(), ticket.getSlaClock(), now),
                autoCloseAt);
    }
}
