package com.itsm.backend.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.itsm.backend.common.web.PageResponse;
import com.itsm.backend.group.GroupDirectory;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketAssignment;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketWorkflow;
import com.itsm.backend.ticket.dto.RatingResponse;
import com.itsm.backend.ticket.dto.SlaResponse;
import com.itsm.backend.ticket.dto.TicketResponse;
import com.itsm.backend.user.UserDirectory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Maps tickets to their API shape; needs the clock (SLA "now"), the auto-close policy and user names. */
@Component
public class TicketResponseAssembler {

    private final Clock clock;
    private final AutoCloseProperties autoClose;
    private final UserDirectory userDirectory;
    private final GroupDirectory groups;

    public TicketResponseAssembler(Clock clock, AutoCloseProperties autoClose, UserDirectory userDirectory,
            GroupDirectory groups) {
        this.clock = clock;
        this.autoClose = autoClose;
        this.userDirectory = userDirectory;
        this.groups = groups;
    }

    public TicketResponse toResponse(Ticket ticket, CurrentUser viewer) {
        return toResponse(ticket, viewer, userDirectory.displayNames(userIds(List.of(ticket))),
                groups.names(Collections.singletonList(ticket.getGroupId())), clock.instant());
    }

    /** Names for the whole page are loaded with a single query. */
    public PageResponse<TicketResponse> toPage(Page<Ticket> page, CurrentUser viewer) {
        Map<String, String> names = userDirectory.displayNames(userIds(page.getContent()));
        Map<Long, String> groupNames = groups.names(page.getContent().stream().map(Ticket::getGroupId).toList());
        Instant now = clock.instant();
        return PageResponse.of(page, ticket -> toResponse(ticket, viewer, names, groupNames, now));
    }

    private TicketResponse toResponse(Ticket ticket, CurrentUser viewer, Map<String, String> names,
            Map<Long, String> groupNames, Instant now) {
        Instant autoCloseAt = ticket.getStatus() == TicketStatus.RESOLVED && ticket.getResolvedAt() != null
                ? ticket.getResolvedAt().plus(autoClose.after())
                : null;
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory(),
                ticket.getGroupId(),
                ticket.getGroupId() == null ? null : groupNames.get(ticket.getGroupId()),
                ticket.getRequesterId(),
                names.get(ticket.getRequesterId()),
                ticket.getAssigneeId(),
                ticket.getAssigneeId() == null ? null : names.get(ticket.getAssigneeId()),
                ticket.getResolutionNote(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                TicketWorkflow.allowedTargets(ticket.getStatus(), viewer, ticket.getRequesterId()),
                SlaResponse.of(ticket.getSlaState(), ticket.getSlaClock(), now),
                autoCloseAt,
                RatingResponse.of(ticket),
                ticket.canBeRatedBy(viewer),
                !ticket.isClosed() && TicketAssignment.canClaim(viewer, ticket.getAssigneeId()),
                !ticket.isClosed() && TicketAssignment.canReassign(viewer, ticket.getAssigneeId()),
                ticket.canBeEditedBy(viewer));
    }

    private static List<String> userIds(List<Ticket> tickets) {
        List<String> ids = new ArrayList<>();
        for (Ticket ticket : tickets) {
            ids.add(ticket.getRequesterId());
            ids.add(ticket.getAssigneeId());
        }
        return ids;
    }
}
