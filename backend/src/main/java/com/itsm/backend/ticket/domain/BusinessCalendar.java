package com.itsm.backend.ticket.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * When SLA clocks run: either always (7/24) or only in working hours on working days that are not holidays.
 * Pure and immutable; see docs/adr/0015-sla-extensions.md.
 */
public final class BusinessCalendar {

    /** Calendar time: every hour counts. The behaviour before working hours existed. */
    public static final BusinessCalendar ALWAYS = new BusinessCalendar(false, ZoneId.of("UTC"),
            EnumSet.allOf(DayOfWeek.class), LocalTime.MIDNIGHT, LocalTime.MAX, Set.of());

    /** Longest stretch searched for working time (a calendar with no working days would never end). */
    private static final int MAX_DAYS = 3 * 366;

    private final boolean enabled;
    private final ZoneId zone;
    private final Set<DayOfWeek> workDays;
    private final LocalTime start;
    private final LocalTime end;
    private final Set<LocalDate> holidays;

    /**
     * @param enabled  false = {@link #ALWAYS} semantics; the other values are kept but not used
     * @param workDays at least one day when enabled
     * @param start    start of the working day; must be before {@code end}
     */
    public BusinessCalendar(boolean enabled, ZoneId zone, Set<DayOfWeek> workDays, LocalTime start, LocalTime end,
            Set<LocalDate> holidays) {
        if (enabled && (workDays.isEmpty() || !start.isBefore(end))) {
            throw new IllegalArgumentException("Working hours need at least one day and start before end");
        }
        this.enabled = enabled;
        this.zone = Objects.requireNonNull(zone);
        this.workDays = workDays.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(workDays));
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.holidays = Set.copyOf(holidays);
    }

    /** Working time between two instants (zero if {@code to} is not after {@code from}). */
    public Duration between(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            return Duration.ZERO;
        }
        if (!enabled) {
            return Duration.between(from, to);
        }
        Duration total = Duration.ZERO;
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        for (int i = 0; !day.isAfter(last) && i <= MAX_DAYS; i++, day = day.plusDays(1)) {
            if (isWorkingDay(day)) {
                Instant open = max(from, opening(day));
                Instant close = min(to, closing(day));
                if (close.isAfter(open)) {
                    total = total.plus(Duration.between(open, close));
                }
            }
        }
        return total;
    }

    /**
     * The instant when {@code amount} of working time has passed after {@code from}. A negative amount goes back in
     * calendar time (only used for already breached clocks, where the exact past moment does not matter).
     */
    public Instant plus(Instant from, Duration amount) {
        if (!enabled || amount.isNegative()) {
            return from.plus(amount);
        }
        Duration left = amount;
        Instant cursor = from;
        LocalDate day = from.atZone(zone).toLocalDate();
        for (int i = 0; i <= MAX_DAYS; i++, day = day.plusDays(1)) {
            if (!isWorkingDay(day)) {
                continue;
            }
            Instant open = max(cursor, opening(day));
            Instant close = closing(day);
            if (!close.isAfter(open)) {
                continue;
            }
            if (left.isZero()) {
                return open;
            }
            Duration available = Duration.between(open, close);
            if (left.compareTo(available) <= 0) {
                return open.plus(left);
            }
            left = left.minus(available);
        }
        throw new IllegalStateException("No working time within " + MAX_DAYS + " days");
    }

    /**
     * Whether both calendars measure working time identically; settings that are not used while disabled are ignored.
     */
    public boolean sameRules(BusinessCalendar other) {
        if (!enabled || !other.enabled) {
            return enabled == other.enabled;
        }
        return zone.equals(other.zone) && workDays.equals(other.workDays) && start.equals(other.start)
                && end.equals(other.end) && holidays.equals(other.holidays);
    }

    public boolean isWorkingDay(LocalDate day) {
        return workDays.contains(day.getDayOfWeek()) && !holidays.contains(day);
    }

    private Instant opening(LocalDate day) {
        return ZonedDateTime.of(day, start, zone).toInstant();
    }

    /** LocalTime.MAX closes at the very end of the day. */
    private Instant closing(LocalDate day) {
        return end.equals(LocalTime.MAX)
                ? day.plusDays(1).atStartOfDay(zone).toInstant()
                : ZonedDateTime.of(day, end, zone).toInstant();
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ZoneId zone() {
        return zone;
    }

    public Set<DayOfWeek> workDays() {
        return workDays;
    }

    public LocalTime start() {
        return start;
    }

    public LocalTime end() {
        return end;
    }

    public Set<LocalDate> holidays() {
        return holidays;
    }
}
