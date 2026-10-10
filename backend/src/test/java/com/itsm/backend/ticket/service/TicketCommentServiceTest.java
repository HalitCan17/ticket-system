package com.itsm.backend.ticket.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.dto.CreateCommentRequest;
import com.itsm.backend.ticket.repository.TicketCommentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TicketCommentServiceTest {

    static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    static final CurrentUser OWNER = new CurrentUser("cust1", Role.CUSTOMER);
    static final CurrentUser AGENT = new CurrentUser("agent1", Role.AGENT);
    static final CurrentUser MANAGER = new CurrentUser("manager1", Role.MANAGER);

    final TicketService ticketService = mock(TicketService.class);
    final TicketCommentRepository comments = mock(TicketCommentRepository.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final TicketCommentService service =
            new TicketCommentService(ticketService, comments, events, Clock.fixed(NOW, ZoneOffset.UTC));

    final Ticket ticket = Ticket.open("VPN", "down", TicketPriority.HIGH, "cust1", Duration.ofHours(8), NOW);

    @Test
    void customerListsOnlyExternalComments() {
        when(ticketService.getVisibleTicket(OWNER, 1L)).thenReturn(ticket);

        service.list(OWNER, 1L);

        verify(comments).findByTicketIdAndVisibilityOrderByCreatedAtAscIdAsc(1L, CommentVisibility.EXTERNAL);
        verify(comments, never()).findByTicketIdOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    void staffListsAllComments() {
        when(ticketService.getVisibleTicket(AGENT, 1L)).thenReturn(ticket);

        service.list(AGENT, 1L);

        verify(comments).findByTicketIdOrderByCreatedAtAscIdAsc(1L);
    }

    @Test
    void customerCannotWriteInternalComment() {
        when(ticketService.getVisibleTicket(OWNER, 1L)).thenReturn(ticket);

        assertThatThrownBy(() -> service.add(OWNER, 1L, new CreateCommentRequest("hi", CommentVisibility.INTERNAL)))
                .isInstanceOf(ForbiddenException.class);
        verify(comments, never()).save(any());
    }

    @Test
    void closedTicketAcceptsNoComments() {
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, NOW);
        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "fixed", NOW);
        ticket.transitionTo(TicketStatus.CLOSED, MANAGER, null, NOW);
        when(ticketService.getVisibleTicket(AGENT, 1L)).thenReturn(ticket);

        assertThatThrownBy(() -> service.add(AGENT, 1L, new CreateCommentRequest("late", CommentVisibility.INTERNAL)))
                .isInstanceOf(ConflictException.class);
        verify(comments, never()).save(any());
    }
}
