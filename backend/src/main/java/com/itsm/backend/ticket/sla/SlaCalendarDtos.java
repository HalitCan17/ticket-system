package com.itsm.backend.ticket.sla;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes of {@code /api/sla/calendar}. */
public final class SlaCalendarDtos {

    private SlaCalendarDtos() {
    }

    public record HolidayDto(@NotNull LocalDate date, @NotBlank @Size(max = 100) String name) {
    }

    /**
     * @param zoneId   IANA zone such as {@code Europe/Istanbul}
     * @param start    {@code HH:mm}
     * @param end      {@code HH:mm}, after {@code start}
     * @param holidays the whole list: holidays missing here are removed
     */
    public record CalendarRequest(
            boolean enabled,
            @NotBlank @Size(max = 64) String zoneId,
            @NotNull @Size(max = 7) List<DayOfWeek> workDays,
            @NotBlank @Size(max = 5) String start,
            @NotBlank @Size(max = 5) String end,
            @NotNull @Size(max = 500) List<@Valid HolidayDto> holidays) {
    }

    /**
     * The calendar and the (read-only) SLA targets it is applied to.
     *
     * @param resolutionSeconds    resolution target per priority
     * @param firstResponseSeconds first response target per priority
     * @param raisePriorityOnBreach whether a breached resolution SLA raises the priority
     */
    public record CalendarResponse(
            boolean enabled,
            String zoneId,
            List<DayOfWeek> workDays,
            String start,
            String end,
            List<HolidayDto> holidays,
            Map<TicketPriority, Long> resolutionSeconds,
            Map<TicketPriority, Long> firstResponseSeconds,
            boolean raisePriorityOnBreach) {
    }

    /** @param recalculatedTickets open tickets whose due and warning moments were recalculated */
    public record CalendarUpdateResponse(CalendarResponse calendar, int recalculatedTickets) {
    }
}
