package com.itsm.backend.ticket.sla;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketPriority;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resolution-time SLA targets per priority ({@code itsm.sla.targets.<PRIORITY>}, e.g. {@code HIGH: 8h}).
 * Priorities not configured fall back to {@link #DEFAULT_TARGETS}. A ticket copies its target when it is
 * opened, so changing these values does not affect open tickets.
 */
@ConfigurationProperties("itsm.sla")
public record SlaProperties(Map<TicketPriority, Duration> targets) {

    public static final Map<TicketPriority, Duration> DEFAULT_TARGETS = Map.of(
            TicketPriority.CRITICAL, Duration.ofHours(4),
            TicketPriority.HIGH, Duration.ofHours(8),
            TicketPriority.MEDIUM, Duration.ofHours(24),
            TicketPriority.LOW, Duration.ofHours(72));

    public SlaProperties {
        Map<TicketPriority, Duration> merged = new EnumMap<>(DEFAULT_TARGETS);
        if (targets != null) {
            merged.putAll(targets);
        }
        merged.forEach((priority, target) -> {
            if (target == null || target.isZero() || target.isNegative() || target.toSeconds() < 1) {
                throw new IllegalArgumentException("itsm.sla.targets." + priority + " must be at least 1s");
            }
        });
        targets = Collections.unmodifiableMap(merged);
    }

    public Duration targetFor(TicketPriority priority) {
        return targets.get(priority);
    }
}
