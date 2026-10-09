package com.itsm.backend.ticket.service;

import java.time.Clock;
import java.util.List;

import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketComment;
import com.itsm.backend.ticket.dto.CreateCommentRequest;
import com.itsm.backend.ticket.repository.TicketCommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments: INTERNAL ones are staff-only; customers read and write EXTERNAL ones only. */
@Service
public class TicketCommentService {

    private final TicketService ticketService;
    private final TicketCommentRepository comments;
    private final Clock clock;

    public TicketCommentService(TicketService ticketService, TicketCommentRepository comments, Clock clock) {
        this.ticketService = ticketService;
        this.comments = comments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TicketComment> list(CurrentUser user, long ticketId) {
        ticketService.getVisibleTicket(user, ticketId);
        return user.isStaff()
                ? comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)
                : comments.findByTicketIdAndVisibilityOrderByCreatedAtAscIdAsc(ticketId, CommentVisibility.EXTERNAL);
    }

    @Transactional
    public TicketComment add(CurrentUser user, long ticketId, CreateCommentRequest request) {
        Ticket ticket = ticketService.getVisibleTicket(user, ticketId);
        if (request.visibility() == CommentVisibility.INTERNAL && !user.isStaff()) {
            throw new ForbiddenException("INTERNAL_COMMENT_NOT_ALLOWED", "Customers can only add external comments.");
        }
        if (ticket.isClosed()) {
            throw new ConflictException("TICKET_CLOSED", "Comments cannot be added to a closed ticket.");
        }
        return comments.save(new TicketComment(
                ticket.getId(), user.id(), request.visibility(), request.body().strip(), clock.instant()));
    }
}
