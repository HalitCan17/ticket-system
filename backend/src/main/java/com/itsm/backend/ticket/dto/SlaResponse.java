package com.itsm.backend.ticket.dto;

import java.time.Instant;

import com.itsm.backend.ticket.domain.BusinessCalendar;
import com.itsm.backend.ticket.domain.SlaClock;
import com.itsm.backend.ticket.domain.SlaState;

/**
 * Resolution-time SLA as of the response time.
 *
 * @param remainingSeconds negative once the target is exceeded
 * @param dueAt            when the target is reached if work continues; null unless the clock is running
 */
public record SlaResponse(
        SlaState state,
        long targetSeconds,
        long elapsedSeconds,
        long remainingSeconds,
        Instant dueAt,
        boolean breached) {

    public static SlaResponse of(SlaState state, SlaClock clock, Instant now) {
        return of(state, clock, BusinessCalendar.ALWAYS, now);
    }

    /** Times count as the business calendar says; the due moment is the real instant the target is reached. */
    public static SlaResponse of(SlaState state, SlaClock clock, BusinessCalendar calendar, Instant now) {
        return new SlaResponse(
                state,
                clock.target().toSeconds(),
                clock.elapsed(now, calendar).toSeconds(),
                clock.remaining(now, calendar).toSeconds(),
                clock.dueAt(now, calendar),
                clock.isBreached(now, calendar));
    }
}
