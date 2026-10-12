package com.itsm.backend.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SlaClockTest {

    static final Instant T0 = Instant.parse("2026-10-10T08:00:00Z");
    static final Duration TARGET = Duration.ofHours(8);

    @Test
    void startsRunning() {
        SlaClock clock = SlaClock.start(TARGET, T0);
        Instant now = T0.plus(Duration.ofHours(1));

        assertThat(clock.isRunning()).isTrue();
        assertThat(clock.elapsed(now)).isEqualTo(Duration.ofHours(1));
        assertThat(clock.remaining(now)).isEqualTo(Duration.ofHours(7));
        assertThat(clock.dueAt(now)).isEqualTo(T0.plus(TARGET));
        assertThat(clock.isBreached(now)).isFalse();
    }

    @Test
    void pausedTimeIsNotCounted() {
        SlaClock paused = SlaClock.start(TARGET, T0).halt(T0.plus(Duration.ofHours(2)));

        // Three days later nothing more has been used and there is no due date while paused.
        Instant later = T0.plus(Duration.ofDays(3));
        assertThat(paused.isRunning()).isFalse();
        assertThat(paused.elapsed(later)).isEqualTo(Duration.ofHours(2));
        assertThat(paused.dueAt(later)).isNull();
        assertThat(paused.isBreached(later)).isFalse();

        SlaClock resumed = paused.resume(later);
        assertThat(resumed.elapsed(later.plus(Duration.ofHours(1)))).isEqualTo(Duration.ofHours(3));
        assertThat(resumed.dueAt(later)).isEqualTo(later.plus(Duration.ofHours(6)));
    }

    @Test
    void severalPausesAccumulate() {
        SlaClock clock = SlaClock.start(TARGET, T0)
                .halt(T0.plus(Duration.ofHours(1)))      // 1h used
                .resume(T0.plus(Duration.ofHours(5)))
                .halt(T0.plus(Duration.ofHours(7)))      // +2h
                .resume(T0.plus(Duration.ofHours(20)))
                .halt(T0.plus(Duration.ofHours(23)));    // +3h

        assertThat(clock.elapsed(T0.plus(Duration.ofDays(10)))).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void breachedOnlyWhenTargetIsExceeded() {
        SlaClock clock = SlaClock.start(TARGET, T0);

        assertThat(clock.isBreached(T0.plus(TARGET))).isFalse();
        assertThat(clock.remaining(T0.plus(TARGET))).isZero();
        assertThat(clock.isBreached(T0.plus(TARGET).plusSeconds(1))).isTrue();
        assertThat(clock.remaining(T0.plus(TARGET).plusSeconds(60))).isEqualTo(Duration.ofSeconds(-60));
    }

    @Test
    void haltAndResumeAreIdempotent() {
        SlaClock running = SlaClock.start(TARGET, T0);
        assertThat(running.resume(T0.plusSeconds(30))).isEqualTo(running);

        SlaClock halted = running.halt(T0.plusSeconds(60));
        assertThat(halted.halt(T0.plusSeconds(120))).isEqualTo(halted);
    }

    @Test
    void clockSkewDoesNotProduceNegativeTime() {
        SlaClock clock = SlaClock.start(TARGET, T0);

        assertThat(clock.elapsed(T0.minusSeconds(5))).isZero();
    }

    @Test
    void targetMustBePositive() {
        assertThatThrownBy(() -> SlaClock.start(Duration.ZERO, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- business calendar ---

    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    static final BusinessCalendar OFFICE = new BusinessCalendar(true, ISTANBUL,
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), Set.of());

    static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ISTANBUL).toInstant();
    }

    @Test
    void dueAndWarningFollowWorkingHours() {
        // Friday 17:00, 4 h target: 1 h on Friday, 3 h on Monday.
        SlaClock clock = SlaClock.start(Duration.ofHours(4), at("2026-10-16T17:00"));

        assertThat(clock.scheduledDueAt(OFFICE)).isEqualTo(at("2026-10-19T12:00"));
        // 80 % of 4 h = 3 h 12 min: 1 h on Friday + 2 h 12 min on Monday.
        assertThat(clock.scheduledWarnAt(OFFICE)).isEqualTo(at("2026-10-19T11:12"));
        assertThat(clock.scheduledDueAt()).isEqualTo(at("2026-10-16T21:00")); // without a calendar: plain hours
    }

    @Test
    void elapsedCountsOnlyWorkingTimeAndBreachFollowsIt() {
        SlaClock clock = SlaClock.start(Duration.ofHours(4), at("2026-10-16T17:00"));

        assertThat(clock.elapsed(at("2026-10-18T12:00"), OFFICE)).isEqualTo(Duration.ofHours(1)); // weekend
        assertThat(clock.isBreached(at("2026-10-19T12:00"), OFFICE)).isFalse(); // exactly the target
        assertThat(clock.isBreached(at("2026-10-19T12:01"), OFFICE)).isTrue();
        assertThat(clock.isBreached(at("2026-10-19T12:01"))).isTrue();
        assertThat(clock.isWarningDue(at("2026-10-19T11:11"), OFFICE)).isFalse();
        assertThat(clock.isWarningDue(at("2026-10-19T11:12"), OFFICE)).isTrue();
        assertThat(clock.remaining(at("2026-10-19T10:00"), OFFICE)).isEqualTo(Duration.ofHours(2));
        assertThat(clock.dueAt(at("2026-10-19T10:00"), OFFICE)).isEqualTo(at("2026-10-19T12:00"));
    }

    @Test
    void haltAccumulatesWorkingTimeAndResumeContinuesFromThere() {
        SlaClock running = SlaClock.start(Duration.ofHours(4), at("2026-10-16T16:00"));

        SlaClock paused = running.halt(at("2026-10-19T10:00"), OFFICE); // 2 h Friday + 1 h Monday
        assertThat(paused.elapsedBeforeRun()).isEqualTo(Duration.ofHours(3));
        assertThat(paused.isRunning()).isFalse();
        assertThat(paused.scheduledDueAt(OFFICE)).isNull();

        SlaClock resumed = paused.resume(at("2026-10-20T08:00")); // before opening: the hour waits
        assertThat(resumed.scheduledDueAt(OFFICE)).isEqualTo(at("2026-10-20T10:00"));
    }

    @Test
    void alreadyUsedUpTimeStillGivesAWarningMomentAtTheStartOfTheRun() {
        SlaClock clock = new SlaClock(Duration.ofHours(4), Duration.ofHours(4), at("2026-10-19T10:00"));

        assertThat(clock.scheduledWarnAt(OFFICE)).isEqualTo(at("2026-10-19T10:00"));
        assertThat(clock.scheduledDueAt(OFFICE)).isEqualTo(at("2026-10-19T10:00"));
    }
}
