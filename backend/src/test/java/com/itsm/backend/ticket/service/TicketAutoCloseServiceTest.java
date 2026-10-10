package com.itsm.backend.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.ticket.repository.TicketStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

class TicketAutoCloseServiceTest {

    static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    static final CurrentUser AGENT = new CurrentUser("agent1", Role.AGENT);

    final TicketRepository tickets = mock(TicketRepository.class);
    final TicketStatusHistoryRepository history = mock(TicketStatusHistoryRepository.class);
    final TicketAutoCloseService service = new TicketAutoCloseService(tickets, history,
            new AutoCloseProperties(true, Duration.ofDays(7), Duration.ofSeconds(60)),
            mock(PlatformTransactionManager.class));

    @Test
    void closesResolvedTicketPastTheCutoffAndRecordsSystemActor() {
        Ticket ticket = resolvedAt(NOW.minus(Duration.ofDays(8)));
        candidates(1L);
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));

        assertThat(service.closeStaleResolved(NOW)).isEqualTo(1);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        ArgumentCaptor<TicketStatusHistory> entry = ArgumentCaptor.forClass(TicketStatusHistory.class);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getChangedBy()).isEqualTo(TicketAutoCloseService.SYSTEM_ACTOR);
        assertThat(entry.getValue().getNote()).isEqualTo("Auto-closed: no customer response for 7d");
        assertThat(entry.getValue().getFromStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(entry.getValue().getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void queriesWithCutoffOfNowMinusAfter() {
        candidates();

        service.closeStaleResolved(NOW);

        verify(tickets).findAutoCloseCandidateIds(eq(NOW.minus(Duration.ofDays(7))), eq(0L), any());
    }

    @Test
    void skipsTicketThatChangedSinceItWasSelected() {
        // Selected as a candidate, but the customer objected before the transaction loaded it.
        Ticket objected = resolvedAt(NOW.minus(Duration.ofDays(8)));
        objected.transitionTo(TicketStatus.IN_PROGRESS, new CurrentUser("cust1", Role.CUSTOMER), "no", NOW);
        candidates(1L);
        when(tickets.findById(1L)).thenReturn(Optional.of(objected));

        assertThat(service.closeStaleResolved(NOW)).isZero();
        verify(history, never()).save(any());
    }

    @Test
    void doesNotCloseTicketResolvedAfterTheCutoff() {
        Ticket recent = resolvedAt(NOW.minus(Duration.ofDays(6)));
        candidates(1L);
        when(tickets.findById(1L)).thenReturn(Optional.of(recent));

        assertThat(service.closeStaleResolved(NOW)).isZero();
        assertThat(recent.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void conflictOrFailureOnOneTicketDoesNotStopTheOthers() {
        Ticket conflicting = resolvedAt(NOW.minus(Duration.ofDays(9)));
        Ticket broken = resolvedAt(NOW.minus(Duration.ofDays(9)));
        Ticket fine = resolvedAt(NOW.minus(Duration.ofDays(9)));
        candidates(1L, 2L, 3L);
        when(tickets.findById(1L)).thenReturn(Optional.of(conflicting));
        when(tickets.findById(2L)).thenReturn(Optional.of(broken));
        when(tickets.findById(3L)).thenReturn(Optional.of(fine));
        when(history.save(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Ticket.class, 1L))
                .thenThrow(new IllegalStateException("database hiccup"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.closeStaleResolved(NOW)).isEqualTo(1);
        assertThat(fine.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void describesDurationInLargestWholeUnit() {
        assertThat(TicketAutoCloseService.describe(Duration.ofDays(7))).isEqualTo("7d");
        assertThat(TicketAutoCloseService.describe(Duration.ofHours(36))).isEqualTo("36h");
        assertThat(TicketAutoCloseService.describe(Duration.ofSeconds(90))).isEqualTo("90s");
    }

    private void candidates(Long... ids) {
        when(tickets.findAutoCloseCandidateIds(any(), anyLong(), any())).thenReturn(List.of(ids), List.of());
    }

    private static Ticket resolvedAt(Instant resolvedAt) {
        Ticket ticket = Ticket.open("t", "d", TicketPriority.LOW, "cust1", Duration.ofHours(72),
                resolvedAt.minus(Duration.ofHours(1)));
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, resolvedAt.minus(Duration.ofMinutes(30)));
        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "fixed", resolvedAt);
        return ticket;
    }
}
