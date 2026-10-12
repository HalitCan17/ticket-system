package com.itsm.backend.report;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.itsm.backend.report.SummaryResponse.RatingSummary;
import com.itsm.backend.report.SummaryResponse.ResolutionSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate SQL for the manager reports; counting happens in the database, tickets are never loaded.
 * "Open" means NEW, IN_PROGRESS or WAITING_FOR_CUSTOMER. The SLA breach test is the one used by
 * {@code TicketRepository#findSlaBreachCandidateIds}.
 */
@Repository
public class ReportQueries {

    private static final String OPEN = "status in ('NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER')";
    private static final String BREACHED = """
            sla_elapsed_seconds
                + coalesce(extract(epoch from (cast(:now as timestamptz) - sla_running_since)), 0)
                > sla_target_seconds""";

    private final NamedParameterJdbcTemplate jdbc;

    public ReportQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Current number of tickets per status (every status present, zero if none). */
    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED")) {
            counts.put(status, 0L);
        }
        jdbc.query("select status, count(*) as n from tickets group by status", new MapSqlParameterSource(),
                rs -> {
                    counts.put(rs.getString("status"), rs.getLong("n"));
                });
        return counts;
    }

    /** Open, open-and-unassigned and open-and-breached counts at {@code now}. */
    public long[] openWork(Instant now) {
        return jdbc.queryForObject("""
                select count(*) filter (where %1$s) as open,
                       count(*) filter (where %1$s and assignee_id is null) as unassigned,
                       count(*) filter (where %1$s and %2$s) as breached
                from tickets""".formatted(OPEN, BREACHED),
                params(null, now),
                (rs, i) -> new long[] {rs.getLong("open"), rs.getLong("unassigned"), rs.getLong("breached")});
    }

    /** First responses given in the period and how many were within their target. */
    public SummaryResponse.FirstResponseSummary firstResponsesBetween(Instant from, Instant now) {
        return jdbc.queryForObject("""
                select count(*) as responded,
                       count(*) filter (where first_responded_at <= first_response_due_at) as within_target
                from tickets
                where first_responded_at >= :from and first_responded_at <= :now""",
                params(from, now),
                (rs, i) -> {
                    long responded = rs.getLong("responded");
                    long within = rs.getLong("within_target");
                    return new SummaryResponse.FirstResponseSummary(responded, within,
                            responded == 0 ? null : (double) within / responded);
                });
    }

    /** Open tickets that were escalated. */
    public long escalatedOpen() {
        Long n = jdbc.queryForObject(
                "select count(*) from tickets where escalated_at is not null and " + OPEN,
                new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    public long createdBetween(Instant from, Instant now) {
        Long n = jdbc.queryForObject("select count(*) from tickets where created_at >= :from and created_at <= :now",
                params(from, now), Long.class);
        return n == null ? 0 : n;
    }

    /** Tickets resolved in the period (still resolved, or closed after it; an objection clears resolved_at). */
    public ResolutionSummary resolvedBetween(Instant from, Instant now) {
        return jdbc.queryForObject("""
                select count(*) as resolved,
                       count(*) filter (where sla_elapsed_seconds <= sla_target_seconds) as within_sla,
                       avg(sla_elapsed_seconds) as avg_seconds
                from tickets
                where resolved_at >= :from and resolved_at <= :now""",
                params(from, now),
                (rs, i) -> {
                    long resolved = rs.getLong("resolved");
                    long within = rs.getLong("within_sla");
                    double avg = rs.getDouble("avg_seconds");
                    return new ResolutionSummary(resolved, within,
                            resolved == 0 ? null : (double) within / resolved,
                            resolved == 0 ? null : Math.round(avg));
                });
    }

    public RatingSummary ratingsBetween(Instant from, Instant now) {
        long[] distribution = new long[5];
        jdbc.query("""
                select rating, count(*) as n from tickets
                where rated_at >= :from and rated_at <= :now
                group by rating""",
                params(from, now),
                rs -> {
                    distribution[rs.getInt("rating") - 1] = rs.getLong("n");
                });
        long count = 0;
        long sum = 0;
        for (int i = 0; i < 5; i++) {
            count += distribution[i];
            sum += distribution[i] * (i + 1);
        }
        return new RatingSummary(count, count == 0 ? null : (double) sum / count,
                List.of(distribution[0], distribution[1], distribution[2], distribution[3], distribution[4]));
    }

    /** Tickets opened in the period per category (every category present). */
    public Map<String, Long> categoriesBetween(Instant from, Instant now) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String category : List.of("HARDWARE", "SOFTWARE", "NETWORK", "ACCESS", "OTHER")) {
            counts.put(category, 0L);
        }
        jdbc.query("""
                select category, count(*) as n from tickets
                where created_at >= :from and created_at <= :now
                group by category""",
                params(from, now),
                rs -> {
                    counts.put(rs.getString("category"), rs.getLong("n"));
                });
        return counts;
    }

    /**
     * One row per staff member: known agents/managers plus anyone holding open tickets. Resolutions and ratings are
     * credited to the ticket's (current) assignee. Most open work first.
     */
    public List<TeamMemberResponse> team(Instant from, Instant now) {
        return jdbc.query("""
                with people as (
                    select id from app_users where role in ('AGENT', 'MANAGER')
                    union
                    select distinct assignee_id from tickets where assignee_id is not null and %1$s
                )
                select p.id,
                       coalesce(u.display_name, u.username, p.id) as name,
                       u.role,
                       count(t.id) filter (where t.%1$s) as open_work,
                       count(t.id) filter (where t.%1$s and %2$s) as breached,
                       count(t.id) filter (where t.resolved_at >= :from and t.resolved_at <= :now) as resolved,
                       count(t.rating) filter (where t.rated_at >= :from and t.rated_at <= :now) as ratings,
                       avg(t.rating) filter (where t.rated_at >= :from and t.rated_at <= :now) as avg_rating
                from people p
                left join app_users u on u.id = p.id
                left join tickets t on t.assignee_id = p.id
                group by p.id, u.display_name, u.username, u.role
                order by open_work desc, breached desc, lower(coalesce(u.display_name, u.username, p.id)), p.id"""
                        .formatted(OPEN, BREACHED.replace("sla_", "t.sla_")),
                params(from, now),
                (rs, i) -> {
                    long ratings = rs.getLong("ratings");
                    return new TeamMemberResponse(rs.getString("id"), rs.getString("name"), rs.getString("role"),
                            rs.getLong("open_work"), rs.getLong("breached"), rs.getLong("resolved"), ratings,
                            ratings == 0 ? null : rs.getDouble("avg_rating"));
                });
    }

    /**
     * Open work per support group (every group, also empty ones), plus a "no group" row when such tickets exist.
     * Most open work first.
     */
    public List<GroupWorkloadResponse> groups(Instant now) {
        String breached = BREACHED.replace("sla_", "t.sla_");
        List<GroupWorkloadResponse> rows = new ArrayList<>(jdbc.query("""
                select g.id, g.name,
                       (select count(*) from support_group_members m where m.group_id = g.id) as members,
                       count(t.id) as open_work,
                       count(t.id) filter (where t.assignee_id is null) as unassigned,
                       count(t.id) filter (where %2$s) as breached
                from support_groups g
                left join tickets t on t.group_id = g.id and t.%1$s
                group by g.id, g.name
                order by open_work desc, lower(g.name), g.id""".formatted(OPEN, breached),
                params(null, now),
                (rs, i) -> new GroupWorkloadResponse(rs.getLong("id"), rs.getString("name"), rs.getLong("members"),
                        rs.getLong("open_work"), rs.getLong("unassigned"), rs.getLong("breached"))));
        GroupWorkloadResponse none = jdbc.queryForObject("""
                select count(*) as open_work,
                       count(*) filter (where t.assignee_id is null) as unassigned,
                       count(*) filter (where %2$s) as breached
                from tickets t where t.group_id is null and t.%1$s""".formatted(OPEN, breached),
                params(null, now),
                (rs, i) -> new GroupWorkloadResponse(null, null, 0, rs.getLong("open_work"), rs.getLong("unassigned"),
                        rs.getLong("breached")));
        if (none != null && none.openWork() > 0) {
            rows.add(none);
        }
        return rows;
    }

    private static MapSqlParameterSource params(Instant from, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", Timestamp.from(now));
        if (from != null) {
            params.addValue("from", Timestamp.from(from));
        }
        return params;
    }
}
