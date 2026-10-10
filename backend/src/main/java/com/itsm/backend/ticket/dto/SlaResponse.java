package com.itsm.backend.ticket.dto;

import java.time.Instant;

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
        return new SlaResponse(
                state,
                clock.target().toSeconds(),
                clock.elapsed(now).toSeconds(),
                clock.remaining(now).toSeconds(),
                clock.dueAt(now),
                clock.isBreached(now));
    }
}
