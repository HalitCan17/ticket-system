package com.itsm.backend.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manager summary; see docs/adr/0013-manager-dashboard.md.
 *
 * @param from           start of the period (now minus {@code days})
 * @param statusCounts   current tickets per status
 * @param openTickets    NEW, IN_PROGRESS or WAITING_FOR_CUSTOMER now
 * @param unassignedOpen open and nobody assigned
 * @param breachedOpen   open and past the SLA target now
 * @param created        opened in the period
 * @param categories     opened in the period, per category
 * @param firstResponse  first responses given in the period
 * @param escalatedOpen  open tickets escalated because their resolution SLA was breached
 */
public record SummaryResponse(
        int days,
        Instant from,
        Instant to,
        Map<String, Long> statusCounts,
        long openTickets,
        long unassignedOpen,
        long breachedOpen,
        long created,
        ResolutionSummary resolution,
        RatingSummary satisfaction,
        Map<String, Long> categories,
        FirstResponseSummary firstResponse,
        long escalatedOpen) {

    /**
     * @param withinSlaRate     share of resolved tickets that met the SLA (0–1); null if none were resolved
     * @param avgResolveSeconds average time the SLA clock counted (customer waiting excluded); null if none
     */
    public record ResolutionSummary(long resolved, long withinSla, Double withinSlaRate, Long avgResolveSeconds) {
    }

    /**
     * Tickets of the period that got their first response: how many in time. {@code withinTargetRate} is null when
     * none were answered.
     */
    public record FirstResponseSummary(long responded, long withinTarget, Double withinTargetRate) {
    }

    /**
     * @param average      null without ratings
     * @param distribution number of 1..5 star ratings, index 0 = 1 star
     */
    public record RatingSummary(long count, Double average, List<Long> distribution) {
    }
}
