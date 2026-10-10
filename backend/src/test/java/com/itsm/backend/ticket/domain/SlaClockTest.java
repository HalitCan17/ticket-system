package com.itsm.backend.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

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
}
