package com.itsm.backend.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Optional;

import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;
import com.itsm.backend.ticket.dto.TransitionRequest;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.ticket.repository.TicketStatusHistoryRepository;
import com.itsm.backend.ticket.sla.SlaProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class TicketServiceTest {

    static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    static final CurrentUser OWNER = new CurrentUser("cust1", Role.CUSTOMER);
    static final CurrentUser OTHER_CUSTOMER = new CurrentUser("cust2", Role.CUSTOMER);
    static final CurrentUser AGENT = new CurrentUser("agent1", Role.AGENT);

    final TicketRepository tickets = mock(TicketRepository.class);
    final TicketStatusHistoryRepository history = mock(TicketStatusHistoryRepository.class);
    final TicketService service = new TicketService(
            tickets, history, new SlaProperties(null), Clock.fixed(NOW, ZoneOffset.UTC));

    final Ticket ticket = Ticket.open(
            "VPN", "down", TicketPriority.HIGH, "cust1", Duration.ofHours(8), NOW.minusSeconds(3600));

    @Test
    void ownerAndStaffCanSeeTicket() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));

        assertThat(service.get(OWNER, 1L)).isSameAs(ticket);
        assertThat(service.get(AGENT, 1L)).isSameAs(ticket);
    }

    @Test
    void otherCustomerGetsNotFoundNotForbidden() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.get(OTHER_CUSTOMER, 1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.transition(OTHER_CUSTOMER, 1L, new TransitionRequest(TicketStatus.CLOSED, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void customerListIsScopedToRequester() {
        Pageable pageable = Pageable.ofSize(20);

        service.list(OWNER, null, pageable);
        service.list(OWNER, TicketStatus.NEW, pageable);
        service.list(AGENT, null, pageable);

        verify(tickets).findByRequesterId("cust1", pageable);
        verify(tickets).findByRequesterIdAndStatus("cust1", TicketStatus.NEW, pageable);
        verify(tickets).findAll(pageable);
    }

    @Test
    void transitionWritesHistoryWithNormalizedNote() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));

        service.transition(AGENT, 1L, new TransitionRequest(TicketStatus.IN_PROGRESS, "   "));

        ArgumentCaptor<TicketStatusHistory> entry = ArgumentCaptor.forClass(TicketStatusHistory.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getFromStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(entry.getValue().getToStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(entry.getValue().getChangedBy()).isEqualTo("agent1");
        assertThat(entry.getValue().getNote()).isNull();
        assertThat(entry.getValue().getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectedTransitionWritesNoHistory() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.transition(OWNER, 1L, new TransitionRequest(TicketStatus.IN_PROGRESS, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(history, never()).save(any());
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
    }
}
