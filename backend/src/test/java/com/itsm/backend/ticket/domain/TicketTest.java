package com.itsm.backend.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import com.itsm.backend.common.error.ApiException;
import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.junit.jupiter.api.Test;

class TicketTest {

    static final CurrentUser CUSTOMER = new CurrentUser("cust1", Role.CUSTOMER);
    static final CurrentUser AGENT = new CurrentUser("agent1", Role.AGENT);
    static final Instant T0 = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void opensAsNew() {
        Ticket ticket = newTicket();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(ticket.getRequesterId()).isEqualTo("cust1");
        assertThat(ticket.getAssigneeId()).isNull();
        assertThat(ticket.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void startingAssignsUnassignedTicketToActor() {
        Ticket ticket = newTicket();

        TicketStatus previous = ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plusSeconds(60));

        assertThat(previous).isEqualTo(TicketStatus.NEW);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAssigneeId()).isEqualTo("agent1");
        assertThat(ticket.getUpdatedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void resolveObjectAndClose() {
        Ticket ticket = newTicket();
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0);

        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "  Restarted the service  ", T0.plusSeconds(10));
        assertThat(ticket.getResolutionNote()).isEqualTo("Restarted the service");
        assertThat(ticket.getResolvedAt()).isEqualTo(T0.plusSeconds(10));

        ticket.transitionTo(TicketStatus.IN_PROGRESS, CUSTOMER, "Still broken", T0.plusSeconds(20));
        assertThat(ticket.getResolutionNote()).isNull();
        assertThat(ticket.getResolvedAt()).isNull();

        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "Replaced the disk", T0.plusSeconds(30));
        ticket.transitionTo(TicketStatus.CLOSED, CUSTOMER, null, T0.plusSeconds(40));
        assertThat(ticket.isClosed()).isTrue();
        assertThat(ticket.getClosedAt()).isEqualTo(T0.plusSeconds(40));
        assertThat(ticket.getResolutionNote()).isEqualTo("Replaced the disk");
    }

    @Test
    void slaClockFollowsStatus() {
        Ticket ticket = newTicket();                                                    // NEW: running
        assertThat(ticket.getSlaState()).isEqualTo(SlaState.RUNNING);
        assertThat(ticket.getSlaClock().target()).isEqualTo(Duration.ofHours(8));

        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plus(Duration.ofHours(1)));
        ticket.transitionTo(TicketStatus.WAITING_FOR_CUSTOMER, AGENT, null, T0.plus(Duration.ofHours(2)));
        assertThat(ticket.getSlaState()).isEqualTo(SlaState.PAUSED);
        assertThat(ticket.getSlaClock().elapsed(T0.plus(Duration.ofDays(2)))).isEqualTo(Duration.ofHours(2));

        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plus(Duration.ofDays(2)));
        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "fixed", T0.plus(Duration.ofDays(2)).plus(Duration.ofHours(3)));
        assertThat(ticket.getSlaState()).isEqualTo(SlaState.STOPPED);
        assertThat(ticket.getSlaClock().elapsed(T0.plus(Duration.ofDays(9)))).isEqualTo(Duration.ofHours(5));

        // Objection resumes the clock where it stopped.
        Instant objection = T0.plus(Duration.ofDays(3));
        ticket.transitionTo(TicketStatus.IN_PROGRESS, CUSTOMER, "still broken", objection);
        assertThat(ticket.getSlaClock().elapsed(objection.plus(Duration.ofHours(4)))).isEqualTo(Duration.ofHours(9));
        assertThat(ticket.getSlaClock().isBreached(objection.plus(Duration.ofHours(4)))).isTrue();
    }

    @Test
    void autoCloseClosesResolvedTicket() {
        Ticket ticket = newTicket();
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0);
        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "fixed", T0.plusSeconds(60));

        ticket.autoClose(T0.plus(Duration.ofDays(7)));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
        assertThat(ticket.getSlaState()).isEqualTo(SlaState.STOPPED);
    }

    @Test
    void autoCloseRejectsTicketsThatAreNotResolved() {
        Ticket ticket = newTicket();

        assertThatThrownBy(() -> ticket.autoClose(T0)).isInstanceOf(ConflictException.class);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
    }

    @Test
    void requesterCanWithdrawNewTicket() {
        Ticket ticket = newTicket();

        ticket.transitionTo(TicketStatus.CLOSED, CUSTOMER, null, T0.plusSeconds(60));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(ticket.getSlaState()).isEqualTo(SlaState.STOPPED);
        // A withdrawn ticket was never resolved, so there is nothing to rate.
        assertThat(ticket.canBeRatedBy(CUSTOMER)).isFalse();
    }

    @Test
    void requesterRatesResolvedTicketOnce() {
        Ticket ticket = resolvedTicket();
        assertThat(ticket.canBeRatedBy(CUSTOMER)).isTrue();
        assertThat(ticket.canBeRatedBy(AGENT)).isFalse();

        ticket.rate(CUSTOMER, 4, "  Hızlı çözüldü ", T0.plusSeconds(300));

        assertThat(ticket.getRating()).isEqualTo(4);
        assertThat(ticket.getRatingComment()).isEqualTo("Hızlı çözüldü");
        assertThat(ticket.getRatedAt()).isEqualTo(T0.plusSeconds(300));
        assertThat(ticket.canBeRatedBy(CUSTOMER)).isFalse();
        assertThatThrownBy(() -> ticket.rate(CUSTOMER, 5, null, T0.plusSeconds(400)))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("ALREADY_RATED");
    }

    @Test
    void closedAfterResolutionCanStillBeRatedButBlankCommentIsDropped() {
        Ticket ticket = resolvedTicket();
        ticket.transitionTo(TicketStatus.CLOSED, CUSTOMER, null, T0.plusSeconds(200));

        ticket.rate(CUSTOMER, 2, "   ", T0.plusSeconds(300));

        assertThat(ticket.getRating()).isEqualTo(2);
        assertThat(ticket.getRatingComment()).isNull();
    }

    @Test
    void unresolvedTicketCannotBeRatedAndOnlyTheRequesterMayRate() {
        Ticket open = newTicket();
        assertThatThrownBy(() -> open.rate(CUSTOMER, 5, null, T0))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("TICKET_NOT_RESOLVED");

        Ticket resolved = resolvedTicket();
        assertThatThrownBy(() -> resolved.rate(AGENT, 5, null, T0))
                .isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("RATING_NOT_ALLOWED");
        assertThat(resolved.getRating()).isNull();
    }

    @Test
    void slaDueAtFollowsTheClock() {
        Ticket ticket = newTicket(); // HIGH, 8 h, running since T0
        assertThat(ticket.getSlaDueAt()).isEqualTo(T0.plus(Duration.ofHours(8)));

        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plusSeconds(3600));
        ticket.transitionTo(TicketStatus.WAITING_FOR_CUSTOMER, AGENT, null, T0.plusSeconds(7200));
        assertThat(ticket.getSlaDueAt()).isNull(); // paused

        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plusSeconds(10_800));
        // 2 h used, 6 h left from the resume
        assertThat(ticket.getSlaDueAt()).isEqualTo(T0.plusSeconds(10_800).plus(Duration.ofHours(6)));
    }

    @Test
    void priorityChangeKeepsUsedTimeAndMovesTheDeadline() {
        Ticket ticket = newTicket(); // HIGH, 8 h
        Instant now = T0.plus(Duration.ofHours(5));

        TicketPriority previous = ticket.changePriority(TicketPriority.CRITICAL, Duration.ofHours(4), Duration.ofHours(1), "Üretim durdu",
                AGENT, now);

        assertThat(previous).isEqualTo(TicketPriority.HIGH);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.getSlaClock().target()).isEqualTo(Duration.ofHours(4));
        assertThat(ticket.getSlaClock().isBreached(now)).isTrue(); // 5 h used of 4 h
        assertThat(ticket.getSlaDueAt()).isEqualTo(T0.plus(Duration.ofHours(4)));
    }

    @Test
    void loweringThePriorityClearsTheBreachMarkSoALaterBreachIsNotified() {
        Ticket ticket = newTicket(); // HIGH, 8 h
        Instant now = T0.plus(Duration.ofHours(9));
        ticket.markSlaBreachNotified(now);

        ticket.changePriority(TicketPriority.LOW, Duration.ofHours(72), Duration.ofHours(1), "Bekleyebilir", AGENT, now);

        assertThat(ticket.getSlaClock().isBreached(now)).isFalse();
        assertThat(ticket.getSlaBreachNotifiedAt()).isNull();
    }

    @Test
    void priorityChangeNeedsStaffAReasonAndAnUnresolvedTicket() {
        Ticket ticket = newTicket();
        assertThatThrownBy(() -> ticket.changePriority(TicketPriority.LOW, Duration.ofHours(72), Duration.ofHours(1), "x", CUSTOMER, T0))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> ticket.changePriority(TicketPriority.LOW, Duration.ofHours(72), Duration.ofHours(1), " ", AGENT, T0))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("PRIORITY_REASON_REQUIRED");

        Ticket resolved = resolvedTicket();
        assertThatThrownBy(() -> resolved.changePriority(TicketPriority.LOW, Duration.ofHours(72), Duration.ofHours(1), "x", AGENT, T0))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("PRIORITY_LOCKED");
        // The category may still change on a resolved ticket.
        resolved.changeCategory(TicketCategory.NETWORK, AGENT, T0);
        assertThat(resolved.getCategory()).isEqualTo(TicketCategory.NETWORK);
    }

    @Test
    void closedTicketCannotBeReassignedOrEdited() {
        Ticket ticket = newTicket();
        ticket.transitionTo(TicketStatus.CLOSED, CUSTOMER, null, T0); // withdrawn

        assertThatThrownBy(() -> ticket.assignTo("agent1", true, AGENT, T0))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("TICKET_CLOSED");
        assertThatThrownBy(() -> ticket.changeCategory(TicketCategory.NETWORK, AGENT, T0))
                .isInstanceOf(ConflictException.class);
        assertThat(ticket.canBeEditedBy(AGENT)).isFalse();
    }

    @Test
    void assignToReturnsThePreviousAssignee() {
        Ticket ticket = newTicket();

        assertThat(ticket.assignTo("agent1", true, AGENT, T0)).isNull();
        assertThat(ticket.assignTo("agent2", true, AGENT, T0.plusSeconds(1))).isEqualTo("agent1");
        assertThat(ticket.getAssigneeId()).isEqualTo("agent2");
        assertThat(ticket.getUpdatedAt()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void firstResponseIsRecordedOnceAndLateIsBreached() {
        Ticket ticket = newTicket();
        ticket.startFirstResponseClock(Duration.ofHours(1));
        assertThat(ticket.getFirstResponseDueAt()).isEqualTo(T0.plus(Duration.ofHours(1)));
        assertThat(ticket.isAwaitingFirstResponse()).isTrue();
        assertThat(ticket.isFirstResponseBreached(T0.plus(Duration.ofMinutes(59)))).isFalse();
        assertThat(ticket.isFirstResponseBreached(T0.plus(Duration.ofMinutes(61)))).isTrue();

        assertThat(ticket.recordFirstResponse(T0.plus(Duration.ofMinutes(70)))).isTrue();
        assertThat(ticket.recordFirstResponse(T0.plus(Duration.ofMinutes(80)))).isFalse(); // only the first counts
        assertThat(ticket.getFirstRespondedAt()).isEqualTo(T0.plus(Duration.ofMinutes(70)));
        assertThat(ticket.isAwaitingFirstResponse()).isFalse();
        assertThat(ticket.isFirstResponseBreached(T0.plus(Duration.ofDays(1)))).isTrue(); // answered late
    }

    @Test
    void aTicketClosedWithoutAnyResponseIsNotALateFirstResponse() {
        Ticket ticket = newTicket();
        ticket.startFirstResponseClock(Duration.ofHours(1));
        ticket.transitionTo(TicketStatus.CLOSED, CUSTOMER, null, T0.plus(Duration.ofHours(2))); // withdrawn

        assertThat(ticket.isAwaitingFirstResponse()).isFalse();
        assertThat(ticket.isFirstResponseBreached(T0.plus(Duration.ofHours(3)))).isFalse();
    }

    @Test
    void priorityChangeMovesAnUnansweredFirstResponseDeadline() {
        Ticket ticket = newTicket(); // HIGH
        ticket.startFirstResponseClock(Duration.ofHours(1));
        ticket.markFirstResponseBreachNotified(T0.plus(Duration.ofHours(2)));

        ticket.changePriority(TicketPriority.LOW, Duration.ofHours(72), Duration.ofHours(8), "Bekleyebilir", AGENT,
                T0.plus(Duration.ofHours(2)));

        assertThat(ticket.getFirstResponseDueAt()).isEqualTo(T0.plus(Duration.ofHours(8)));
        assertThat(ticket.getFirstResponseBreachNotifiedAt()).isNull(); // no longer late: notify again if it gets late
    }

    @Test
    void warningMomentIsEightyPercentOfTheTargetAndFollowsPauses() {
        Ticket ticket = newTicket(); // 8 h target, running since T0
        assertThat(ticket.getSlaWarnAt()).isEqualTo(T0.plus(Duration.ofMinutes(384))); // 6 h 24 min

        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plus(Duration.ofHours(1)));
        ticket.transitionTo(TicketStatus.WAITING_FOR_CUSTOMER, AGENT, null, T0.plus(Duration.ofHours(2)));
        assertThat(ticket.getSlaWarnAt()).isNull(); // paused
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plus(Duration.ofHours(5)));
        // 2 h used: warn 4 h 24 min after the resume
        assertThat(ticket.getSlaWarnAt()).isEqualTo(T0.plus(Duration.ofHours(5)).plus(Duration.ofMinutes(264)));
    }

    @Test
    void escalationMarksTheTicketAndRaisesThePriorityOnlyWhenAsked() {
        Ticket kept = newTicket(); // HIGH
        assertThat(kept.escalate(false, p -> Duration.ofHours(4), p -> Duration.ofMinutes(30), T0)).isNull();
        assertThat(kept.getEscalatedAt()).isEqualTo(T0);
        assertThat(kept.getPriority()).isEqualTo(TicketPriority.HIGH);

        Ticket raised = newTicket();
        raised.startFirstResponseClock(Duration.ofHours(1));
        assertThat(raised.escalate(true, p -> Duration.ofHours(4), p -> Duration.ofMinutes(30), T0))
                .isEqualTo(TicketPriority.HIGH);
        assertThat(raised.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(raised.getSlaClock().target()).isEqualTo(Duration.ofHours(4));
        assertThat(raised.getFirstResponseDueAt()).isEqualTo(T0.plus(Duration.ofMinutes(30)));

        // CRITICAL has nowhere to go.
        assertThat(raised.escalate(true, p -> Duration.ofHours(4), p -> Duration.ofMinutes(30), T0)).isNull();
        assertThat(TicketPriority.LOW.higher()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(TicketPriority.CRITICAL.higher()).isEqualTo(TicketPriority.CRITICAL);
    }

    // --- business calendar ---

    static final java.time.ZoneId ISTANBUL = java.time.ZoneId.of("Europe/Istanbul");
    static final BusinessCalendar OFFICE = new BusinessCalendar(true, ISTANBUL,
            java.util.EnumSet.range(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY),
            java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0), java.util.Set.of());

    static Instant at(String localDateTime) {
        return java.time.LocalDateTime.parse(localDateTime).atZone(ISTANBUL).toInstant();
    }

    @Test
    void ticketOpenedOnFridayEveningIsDueMondayAndFirstResponseWaitsForOpening() {
        Instant opened = at("2026-10-16T17:30");
        Ticket ticket = Ticket.open("VPN", "down", TicketPriority.CRITICAL, TicketCategory.NETWORK, "cust1",
                Duration.ofHours(4), OFFICE, opened);
        ticket.startFirstResponseClock(Duration.ofMinutes(60), OFFICE);

        // 30 min on Friday + 3 h 30 min on Monday
        assertThat(ticket.getSlaDueAt()).isEqualTo(at("2026-10-19T12:30"));
        assertThat(ticket.getFirstResponseDueAt()).isEqualTo(at("2026-10-19T09:30"));
        assertThat(ticket.getSlaClock().isBreached(at("2026-10-18T12:00"), OFFICE)).isFalse();
    }

    @Test
    void pausingSettlesWorkingTimeAndResumingCountsFromTheNextOpening() {
        Ticket ticket = Ticket.open("VPN", "down", TicketPriority.HIGH, TicketCategory.NETWORK, "cust1",
                Duration.ofHours(8), OFFICE, at("2026-10-16T16:00"));
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, OFFICE, at("2026-10-16T16:30"));

        // Waits over the weekend: only the 2 h on Friday count.
        ticket.transitionTo(TicketStatus.WAITING_FOR_CUSTOMER, AGENT, null, OFFICE, at("2026-10-16T18:30"));
        assertThat(ticket.getSlaClock().elapsedBeforeRun()).isEqualTo(Duration.ofHours(2));
        assertThat(ticket.getSlaDueAt()).isNull();

        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, OFFICE, at("2026-10-19T07:00"));
        assertThat(ticket.getSlaDueAt()).isEqualTo(at("2026-10-19T15:00")); // 6 h left, from 09:00
    }

    @Test
    void changingTheCalendarKeepsElapsedTimeAndMovesTheDueMoment() {
        Instant opened = at("2026-10-14T10:00"); // Wednesday
        Ticket ticket = Ticket.open("VPN", "down", TicketPriority.HIGH, TicketCategory.NETWORK, "cust1",
                Duration.ofHours(8), BusinessCalendar.ALWAYS, opened);
        ticket.startFirstResponseClock(Duration.ofHours(1), BusinessCalendar.ALWAYS);
        assertThat(ticket.getSlaDueAt()).isEqualTo(at("2026-10-14T18:00"));

        // 2 h later working hours are switched on: 6 h are left, counted in office hours (until 18:00 = 6 h).
        Instant changed = at("2026-10-14T12:00");
        ticket.rebaseSlaClocks(BusinessCalendar.ALWAYS, OFFICE, Duration.ofHours(1), changed);

        assertThat(ticket.getSlaClock().elapsedBeforeRun()).isEqualTo(Duration.ofHours(2));
        assertThat(ticket.getSlaDueAt()).isEqualTo(at("2026-10-14T18:00")); // 6 h left, Wednesday until closing
        assertThat(ticket.getFirstResponseDueAt()).isEqualTo(at("2026-10-14T11:00"));
    }

    @Test
    void calendarRulesAreComparedIgnoringUnusedSettings() {
        BusinessCalendar sameOffice = new BusinessCalendar(true, ISTANBUL,
                java.util.EnumSet.range(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0), java.util.Set.of());
        BusinessCalendar shorter = new BusinessCalendar(true, ISTANBUL,
                java.util.EnumSet.range(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 0), java.util.Set.of());
        BusinessCalendar disabled = new BusinessCalendar(false, ISTANBUL, java.util.Set.of(),
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(9, 0), java.util.Set.of());

        assertThat(OFFICE.sameRules(sameOffice)).isTrue();
        assertThat(OFFICE.sameRules(shorter)).isFalse();
        assertThat(OFFICE.sameRules(BusinessCalendar.ALWAYS)).isFalse();
        assertThat(disabled.sameRules(BusinessCalendar.ALWAYS)).isTrue();
    }

    private static Ticket resolvedTicket() {
        Ticket ticket = newTicket();
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plusSeconds(60));
        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "Sürücü güncellendi", T0.plusSeconds(120));
        return ticket;
    }

    private static Ticket newTicket() {
        return Ticket.open("VPN down", "Cannot connect", TicketPriority.HIGH, TicketCategory.OTHER, "cust1", Duration.ofHours(8), T0);
    }
}
