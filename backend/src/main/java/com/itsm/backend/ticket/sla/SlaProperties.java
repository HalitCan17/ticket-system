package com.itsm.backend.ticket.sla;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketPriority;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * SLA targets per priority. A ticket copies its targets when it is opened (or its priority changes), so changing
 * these values does not affect open tickets.
 *
 * @param targets       resolution time ({@code itsm.sla.targets.<PRIORITY>}, e.g. {@code HIGH: 8h});
 *                      missing priorities use {@link #DEFAULT_TARGETS}
 * @param firstResponse first customer-visible staff reaction ({@code itsm.sla.first-response.<PRIORITY>});
 *                      missing priorities use {@link #DEFAULT_FIRST_RESPONSE}
 * @param escalation    what happens when the resolution SLA is breached
 */
@ConfigurationProperties("itsm.sla")
public record SlaProperties(Map<TicketPriority, Duration> targets, Map<TicketPriority, Duration> firstResponse,
        Escalation escalation) {

    public static final Map<TicketPriority, Duration> DEFAULT_TARGETS = Map.of(
            TicketPriority.CRITICAL, Duration.ofHours(4),
            TicketPriority.HIGH, Duration.ofHours(8),
            TicketPriority.MEDIUM, Duration.ofHours(24),
            TicketPriority.LOW, Duration.ofHours(72));

    public static final Map<TicketPriority, Duration> DEFAULT_FIRST_RESPONSE = Map.of(
            TicketPriority.CRITICAL, Duration.ofMinutes(30),
            TicketPriority.HIGH, Duration.ofHours(1),
            TicketPriority.MEDIUM, Duration.ofHours(4),
            TicketPriority.LOW, Duration.ofHours(8));

    /** @param raisePriority raise the priority one level when the resolution SLA is breached (CRITICAL stays) */
    public record Escalation(boolean raisePriority) {
    }

    @ConstructorBinding
    public SlaProperties {
        targets = merge(DEFAULT_TARGETS, targets, "targets");
        firstResponse = merge(DEFAULT_FIRST_RESPONSE, firstResponse, "first-response");
        escalation = escalation == null ? new Escalation(false) : escalation;
    }

    /** Resolution targets only; first response and escalation use their defaults. */
    public SlaProperties(Map<TicketPriority, Duration> targets) {
        this(targets, null, null);
    }

    public Duration targetFor(TicketPriority priority) {
        return targets.get(priority);
    }

    public Duration firstResponseFor(TicketPriority priority) {
        return firstResponse.get(priority);
    }

    private static Map<TicketPriority, Duration> merge(Map<TicketPriority, Duration> defaults,
            Map<TicketPriority, Duration> configured, String name) {
        Map<TicketPriority, Duration> merged = new EnumMap<>(defaults);
        if (configured != null) {
            merged.putAll(configured);
        }
        merged.forEach((priority, target) -> {
            if (target == null || target.isZero() || target.isNegative() || target.toSeconds() < 1) {
                throw new IllegalArgumentException("itsm.sla." + name + "." + priority + " must be at least 1s");
            }
        });
        return Collections.unmodifiableMap(merged);
    }
}
