package com.itsm.backend.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    /** 2026-10-29 (Thursday) is Republic Day. */
    static final LocalDate REPUBLIC_DAY = LocalDate.of(2026, 10, 29);
    static final BusinessCalendar OFFICE = new BusinessCalendar(true, ISTANBUL, WEEKDAYS, LocalTime.of(9, 0),
            LocalTime.of(18, 0), Set.of(REPUBLIC_DAY));

    static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ISTANBUL).toInstant();
    }

    @Test
    void alwaysIsPlainCalendarTime() {
        Instant from = at("2026-10-16T17:00");
        assertThat(BusinessCalendar.ALWAYS.plus(from, Duration.ofHours(4))).isEqualTo(at("2026-10-16T21:00"));
        assertThat(BusinessCalendar.ALWAYS.between(from, at("2026-10-19T09:00"))).isEqualTo(Duration.ofHours(64));
        assertThat(BusinessCalendar.ALWAYS.between(at("2026-10-19T09:00"), from)).isZero();
    }

    @Test
    void fridayEveningTicketIsDueMondayMorning() {
        // 2026-10-16 is a Friday: 1 h left that day, 3 h more on Monday.
        assertThat(OFFICE.plus(at("2026-10-16T17:00"), Duration.ofHours(4))).isEqualTo(at("2026-10-19T12:00"));
    }

    @Test
    void timeOutsideHoursStartsAtTheNextOpening() {
        assertThat(OFFICE.plus(at("2026-10-14T07:30"), Duration.ofMinutes(30))).isEqualTo(at("2026-10-14T09:30"));
        assertThat(OFFICE.plus(at("2026-10-14T20:00"), Duration.ofHours(1))).isEqualTo(at("2026-10-15T10:00"));
        assertThat(OFFICE.plus(at("2026-10-17T12:00"), Duration.ofHours(1))).isEqualTo(at("2026-10-19T10:00")); // Saturday
        assertThat(OFFICE.plus(at("2026-10-14T20:00"), Duration.ZERO)).isEqualTo(at("2026-10-15T09:00"));
    }

    @Test
    void exactlyTheRestOfTheDayEndsAtClosingTime() {
        assertThat(OFFICE.plus(at("2026-10-14T09:00"), Duration.ofHours(9))).isEqualTo(at("2026-10-14T18:00"));
    }

    @Test
    void holidaysAreSkipped() {
        // Wednesday 17:00 + 2 h: 1 h on Wednesday, Thursday is a holiday, 1 h on Friday.
        assertThat(OFFICE.plus(at("2026-10-28T17:00"), Duration.ofHours(2))).isEqualTo(at("2026-10-30T10:00"));
        assertThat(OFFICE.isWorkingDay(REPUBLIC_DAY)).isFalse();
    }

    @Test
    void betweenCountsOnlyWorkingTime() {
        assertThat(OFFICE.between(at("2026-10-16T17:00"), at("2026-10-19T12:00"))).isEqualTo(Duration.ofHours(4));
        assertThat(OFFICE.between(at("2026-10-14T07:00"), at("2026-10-14T08:00"))).isZero();
        assertThat(OFFICE.between(at("2026-10-14T10:00"), at("2026-10-14T10:30"))).isEqualTo(Duration.ofMinutes(30));
        assertThat(OFFICE.between(at("2026-10-28T17:00"), at("2026-10-30T10:00"))).isEqualTo(Duration.ofHours(2));
        // A whole working week is 45 h.
        assertThat(OFFICE.between(at("2026-10-12T00:00"), at("2026-10-19T00:00"))).isEqualTo(Duration.ofHours(45));
    }

    @Test
    void plusAndBetweenAgree() {
        Instant from = at("2026-10-15T16:20");
        for (int minutes : new int[] {1, 99, 100, 101, 540, 1000, 4000}) {
            Instant due = OFFICE.plus(from, Duration.ofMinutes(minutes));
            assertThat(OFFICE.between(from, due)).as("%d min", minutes).isEqualTo(Duration.ofMinutes(minutes));
        }
    }

    @Test
    void negativeAmountsGoBackInCalendarTime() {
        assertThat(OFFICE.plus(at("2026-10-14T10:00"), Duration.ofHours(-2))).isEqualTo(at("2026-10-14T08:00"));
    }

    @Test
    void enabledCalendarNeedsWorkingTime() {
        assertThatThrownBy(() -> new BusinessCalendar(true, ISTANBUL, Set.of(), LocalTime.of(9, 0), LocalTime.of(18, 0),
                Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessCalendar(true, ISTANBUL, WEEKDAYS, LocalTime.of(18, 0), LocalTime.of(9, 0),
                Set.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
