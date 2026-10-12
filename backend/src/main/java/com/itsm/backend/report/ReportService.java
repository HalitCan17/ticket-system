package com.itsm.backend.report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manager-only figures over tickets; "now" comes from the clock (tests pass their own). */
@Service
public class ReportService {

    private final ReportQueries queries;
    private final Clock clock;

    public ReportService(ReportQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(CurrentUser user, int days) {
        return summary(user, days, clock.instant());
    }

    /** All figures are read in one transaction, so they describe the same moment. */
    @Transactional(readOnly = true)
    public SummaryResponse summary(CurrentUser user, int days, Instant now) {
        requireManager(user);
        Instant from = now.minus(Duration.ofDays(days));
        long[] open = queries.openWork(now);
        return new SummaryResponse(days, from, now,
                queries.statusCounts(),
                open[0], open[1], open[2],
                queries.createdBetween(from, now),
                queries.resolvedBetween(from, now),
                queries.ratingsBetween(from, now),
                queries.categoriesBetween(from, now));
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> team(CurrentUser user, int days) {
        return team(user, days, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> team(CurrentUser user, int days, Instant now) {
        requireManager(user);
        return queries.team(now.minus(Duration.ofDays(days)), now);
    }

    private static void requireManager(CurrentUser user) {
        if (user.role() != Role.MANAGER) {
            throw new ForbiddenException("MANAGER_ONLY", "Only managers can see reports.");
        }
    }
}
