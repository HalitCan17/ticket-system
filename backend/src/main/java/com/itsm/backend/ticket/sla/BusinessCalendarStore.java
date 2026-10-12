package com.itsm.backend.ticket.sla;

import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.itsm.backend.ticket.domain.BusinessCalendar;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads and writes the business-hours settings and holidays (tables {@code business_hours}, {@code holidays}). */
@Repository
public class BusinessCalendarStore {

    /** A public holiday; the SLA clocks do not run on that day. */
    public record Holiday(LocalDate day, String name) {
    }

    /** The stored settings; {@link #toCalendar()} is what the SLA clocks use. */
    public record Settings(boolean enabled, ZoneId zone, Set<DayOfWeek> workDays, LocalTime start, LocalTime end,
            List<Holiday> holidays, String updatedBy) {

        public BusinessCalendar toCalendar() {
            if (!enabled) {
                return BusinessCalendar.ALWAYS;
            }
            return new BusinessCalendar(true, zone, workDays, start, end,
                    holidays.stream().map(Holiday::day).collect(Collectors.toSet()));
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public BusinessCalendarStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Settings load() {
        List<Holiday> holidays = jdbc.query("select day, name from holidays order by day",
                new MapSqlParameterSource(),
                (rs, i) -> new Holiday(rs.getDate("day").toLocalDate(), rs.getString("name")));
        return jdbc.queryForObject("""
                select enabled, zone_id, work_days, start_time, end_time, updated_by
                from business_hours where id = 1""",
                new MapSqlParameterSource(),
                (rs, i) -> new Settings(
                        rs.getBoolean("enabled"),
                        ZoneId.of(rs.getString("zone_id")),
                        parseDays(rs.getString("work_days")),
                        rs.getTime("start_time").toLocalTime(),
                        rs.getTime("end_time").toLocalTime(),
                        new ArrayList<>(holidays),
                        rs.getString("updated_by")));
    }

    /** Replaces the settings row and the whole holiday list. */
    public void save(Settings settings, java.time.Instant now) {
        jdbc.update("""
                update business_hours
                set enabled = :enabled, zone_id = :zone, work_days = :days, start_time = :start, end_time = :end,
                    updated_by = :by, updated_at = :at
                where id = 1""",
                new MapSqlParameterSource()
                        .addValue("enabled", settings.enabled())
                        .addValue("zone", settings.zone().getId())
                        .addValue("days", settings.workDays().stream().sorted().map(Enum::name)
                                .collect(Collectors.joining(",")))
                        .addValue("start", Time.valueOf(settings.start()))
                        .addValue("end", Time.valueOf(settings.end()))
                        .addValue("by", settings.updatedBy())
                        .addValue("at", java.sql.Timestamp.from(now)));
        jdbc.update("delete from holidays", new MapSqlParameterSource());
        for (Holiday holiday : settings.holidays()) {
            jdbc.update("insert into holidays (day, name) values (:day, :name)",
                    new MapSqlParameterSource().addValue("day", java.sql.Date.valueOf(holiday.day()))
                            .addValue("name", holiday.name()));
        }
    }

    private static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String name : csv.split(",")) {
            if (!name.isBlank()) {
                days.add(DayOfWeek.valueOf(name.strip()));
            }
        }
        return days;
    }
}
