package com.itsm.backend.ticket.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Resolution-time SLA clock. Immutable; every change returns a new clock.
 *
 * <p>Persisted as three values (see V3 migration): the target, the time accumulated by finished
 * running periods and the start of the current running period (null when not running).
 * The clock runs in NEW and IN_PROGRESS, is paused in WAITING_FOR_CUSTOMER and stopped in
 * RESOLVED/CLOSED; an objection resumes it where it left off.
 *
 * <p>While running, time counts as the {@link BusinessCalendar} says: every hour with
 * {@link BusinessCalendar#ALWAYS} (the methods without a calendar), otherwise only working hours.
 * See docs/adr/0005-sla-and-auto-close.md and docs/adr/0015-sla-extensions.md.
 */
public record SlaClock(Duration target, Duration elapsedBeforeRun, Instant runningSince) {

    /** Share of the target after which the assignee is warned (docs/adr/0015-sla-extensions.md). */
    public static final double WARNING_SHARE = 0.8;

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
        return halt(now, BusinessCalendar.ALWAYS);
    }

    public SlaClock halt(Instant now, BusinessCalendar calendar) {
        if (!isRunning()) {
            return this;
        }
        return new SlaClock(target, elapsedBeforeRun.plus(runningFor(now, calendar)), null);
    }

    /** Resumes counting; no-op if the clock is already running. */
    public SlaClock resume(Instant now) {
        if (isRunning()) {
            return this;
        }
        return new SlaClock(target, elapsedBeforeRun, now);
    }

    public Duration elapsed(Instant now) {
        return elapsed(now, BusinessCalendar.ALWAYS);
    }

    public Duration elapsed(Instant now, BusinessCalendar calendar) {
        return elapsedBeforeRun.plus(runningFor(now, calendar));
    }

    /** Time left until the target; negative once breached. */
    public Duration remaining(Instant now) {
        return remaining(now, BusinessCalendar.ALWAYS);
    }

    public Duration remaining(Instant now, BusinessCalendar calendar) {
        return target.minus(elapsed(now, calendar));
    }

    /** Moment the target is reached if the clock keeps running; null while it is not running. */
    public Instant dueAt(Instant now) {
        return dueAt(now, BusinessCalendar.ALWAYS);
    }

    public Instant dueAt(Instant now, BusinessCalendar calendar) {
        return isRunning() ? calendar.plus(now, remaining(now, calendar)) : null;
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
        return scheduledDueAt(BusinessCalendar.ALWAYS);
    }

    public Instant scheduledDueAt(BusinessCalendar calendar) {
        return isRunning() ? calendar.plus(runningSince, target.minus(elapsedBeforeRun)) : null;
    }

    /**
     * Moment {@link #WARNING_SHARE} of the target is used if the clock keeps running (already past: the start of the
     * current run); null while not running.
     */
    public Instant scheduledWarnAt() {
        return scheduledWarnAt(BusinessCalendar.ALWAYS);
    }

    public Instant scheduledWarnAt(BusinessCalendar calendar) {
        if (!isRunning()) {
            return null;
        }
        Duration warnAfter = warnThreshold().minus(elapsedBeforeRun);
        return warnAfter.isNegative() ? runningSince : calendar.plus(runningSince, warnAfter);
    }

    /** Whether {@link #WARNING_SHARE} of the target is used at {@code now}. */
    public boolean isWarningDue(Instant now) {
        return isWarningDue(now, BusinessCalendar.ALWAYS);
    }

    public boolean isWarningDue(Instant now, BusinessCalendar calendar) {
        return elapsed(now, calendar).compareTo(warnThreshold()) >= 0;
    }

    /** Breached once more time was used than allowed; hitting the target exactly is still within SLA. */
    public boolean isBreached(Instant now) {
        return isBreached(now, BusinessCalendar.ALWAYS);
    }

    public boolean isBreached(Instant now, BusinessCalendar calendar) {
        return elapsed(now, calendar).compareTo(target) > 0;
    }

    private Duration warnThreshold() {
        return Duration.ofMillis(Math.round(target.toMillis() * WARNING_SHARE));
    }

    private Duration runningFor(Instant now, BusinessCalendar calendar) {
        if (!isRunning()) {
            return Duration.ZERO;
        }
        return calendar.between(runningSince, now);
    }
}
