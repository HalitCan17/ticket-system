package com.itsm.backend.ticket.sla;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.BusinessCalendar;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.ticket.sla.BusinessCalendarStore.Holiday;
import com.itsm.backend.ticket.sla.BusinessCalendarStore.Settings;
import com.itsm.backend.ticket.sla.SlaCalendarDtos.CalendarRequest;
import com.itsm.backend.ticket.sla.SlaCalendarDtos.CalendarResponse;
import com.itsm.backend.ticket.sla.SlaCalendarDtos.CalendarUpdateResponse;
import com.itsm.backend.ticket.sla.SlaCalendarDtos.HolidayDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Staff read the business calendar; managers change it. Saving recalculates the open tickets' due and warning moments
 * in the same transaction (docs/adr/0015-sla-extensions.md).
 */
@Service
public class SlaCalendarService {

    private static final Logger log = LoggerFactory.getLogger(SlaCalendarService.class);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final BusinessCalendarStore store;
    private final BusinessCalendarProvider provider;
    private final TicketRepository tickets;
    private final SlaProperties sla;
    private final Clock clock;

    public SlaCalendarService(BusinessCalendarStore store, BusinessCalendarProvider provider, TicketRepository tickets,
            SlaProperties sla, Clock clock) {
        this.store = store;
        this.provider = provider;
        this.tickets = tickets;
        this.sla = sla;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalendarResponse get(CurrentUser user) {
        if (!user.isStaff()) {
            throw new ForbiddenException("STAFF_ONLY", "Only staff can see the SLA settings.");
        }
        return toResponse(store.load());
    }

    @Transactional
    public CalendarUpdateResponse update(CurrentUser user, CalendarRequest request) {
        if (user.role() != Role.MANAGER) {
            throw new ForbiddenException("MANAGER_ONLY", "Only managers can change the SLA settings.");
        }
        Settings next = validate(request, user.id());
        Settings previous = store.load();
        Instant now = clock.instant();
        store.save(next, now);

        BusinessCalendar before = previous.toCalendar();
        BusinessCalendar after = next.toCalendar();
        int recalculated = 0;
        if (!before.sameRules(after)) {
            for (Ticket ticket : tickets.findOpenForSlaRebase()) {
                ticket.rebaseSlaClocks(before, after, sla.firstResponseFor(ticket.getPriority()), now);
                recalculated++;
            }
        }
        // Other requests keep the old calendar until this commit; then the cache is dropped.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                provider.invalidate();
            }
        });
        log.info("SLA calendar saved by {} (enabled={}, {} holidays); {} open ticket(s) recalculated", user.id(),
                next.enabled(), next.holidays().size(), recalculated);
        return new CalendarUpdateResponse(toResponse(next), recalculated);
    }

    private static Settings validate(CalendarRequest request, String actorId) {
        ZoneId zone;
        try {
            zone = ZoneId.of(request.zoneId().strip());
        } catch (DateTimeException e) {
            throw invalid("Unknown time zone: " + request.zoneId());
        }
        LocalTime start = parseTime(request.start());
        LocalTime end = parseTime(request.end());
        if (!start.isBefore(end)) {
            throw invalid("The working day must end after it starts.");
        }
        Set<DayOfWeek> days = request.workDays().isEmpty() ? EnumSet.noneOf(DayOfWeek.class)
                : EnumSet.copyOf(request.workDays());
        if (days.isEmpty()) {
            throw invalid("Choose at least one working day.");
        }
        Set<LocalDate> seen = new HashSet<>();
        List<Holiday> holidays = request.holidays().stream()
                .map(h -> new Holiday(h.date(), h.name().strip()))
                .sorted((a, b) -> a.day().compareTo(b.day()))
                .toList();
        for (Holiday holiday : holidays) {
            if (!seen.add(holiday.day())) {
                throw invalid("The same holiday date is listed twice: " + holiday.day());
            }
        }
        return new Settings(request.enabled(), zone, days, start, end, holidays, actorId);
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeException e) {
            throw invalid("Times must look like 09:00.");
        }
    }

    private static BadRequestException invalid(String detail) {
        return new BadRequestException("INVALID_CALENDAR", detail);
    }

    private CalendarResponse toResponse(Settings settings) {
        Map<TicketPriority, Long> resolution = new EnumMap<>(TicketPriority.class);
        Map<TicketPriority, Long> firstResponse = new EnumMap<>(TicketPriority.class);
        for (TicketPriority priority : TicketPriority.values()) {
            resolution.put(priority, sla.targetFor(priority).toSeconds());
            firstResponse.put(priority, sla.firstResponseFor(priority).toSeconds());
        }
        return new CalendarResponse(
                settings.enabled(),
                settings.zone().getId(),
                settings.workDays().stream().sorted().toList(),
                settings.start().format(HH_MM),
                settings.end().format(HH_MM),
                settings.holidays().stream().map(h -> new HolidayDto(h.day(), h.name())).toList(),
                resolution,
                firstResponse,
                sla.escalation().raisePriority());
    }
}
