package com.itsm.backend.ticket.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Resolution-time SLA clock, in calendar time. Immutable; every change returns a new clock.
 *
 * <p>Persisted as three values (see V3 migration): the target, the time accumulated by finished
 * running periods and the start of the current running period (null when not running).
 * The clock runs in NEW and IN_PROGRESS, is paused in WAITING_FOR_CUSTOMER and stopped in
 * RESOLVED/CLOSED; an objection resumes it where it left off.
 * See docs/adr/0005-sla-and-auto-close.md.
 */
public record SlaClock(Duration target, Duration elapsedBeforeRun, Instant runningSince) {

    public SlaClock {
        if (target == null || target.isZero() || target.isNegative()) {
            throw new IllegalArgumentException("SLA target must be positive");
        }
        if (elapsedBeforeRun == null || elapsedBeforeRun.isNegative()) {
            throw new IllegalArgumentException("Elapsed time must not be negative");
        }
    }

    /** A clock that starts running now. */
    public static SlaClock start(Duration target, Instant now) {
        return new SlaClock(target, Duration.ZERO, now);
    }

    public boolean isRunning() {
        return runningSince != null;
    }

    /** Accumulates the current running period; no-op if the clock is not running. */
    public SlaClock halt(Instant now) {
        if (!isRunning()) {
            return this;
        }
        return new SlaClock(target, elapsedBeforeRun.plus(runningFor(now)), null);
    }

    /** Resumes counting; no-op if the clock is already running. */
    public SlaClock resume(Instant now) {
        if (isRunning()) {
            return this;
        }
        return new SlaClock(target, elapsedBeforeRun, now);
    }

    public Duration elapsed(Instant now) {
        return elapsedBeforeRun.plus(runningFor(now));
    }

    /** Time left until the target; negative once breached. */
    public Duration remaining(Instant now) {
        return target.minus(elapsed(now));
    }

    /** Moment the target is reached if the clock keeps running; null while it is not running. */
    public Instant dueAt(Instant now) {
        return isRunning() ? now.plus(remaining(now)) : null;
    }

    /** Same clock measured against another target (priority change); the time used so far is kept. */
    public SlaClock withTarget(Duration newTarget) {
        return new SlaClock(newTarget, elapsedBeforeRun, runningSince);
    }

    /**
     * Moment the target is reached if the clock keeps running, independent of "now" (it only changes
     * when the clock changes); null while not running. Stored so the work queue can sort by it.
     */
    public Instant scheduledDueAt() {
        return isRunning() ? runningSince.plus(target.minus(elapsedBeforeRun)) : null;
    }

    /** Breached once more time was used than allowed; hitting the target exactly is still within SLA. */
    public boolean isBreached(Instant now) {
        return elapsed(now).compareTo(target) > 0;
    }

    private Duration runningFor(Instant now) {
        if (!isRunning()) {
            return Duration.ZERO;
        }
        Duration running = Duration.between(runningSince, now);
        return running.isNegative() ? Duration.ZERO : running;
    }
}
