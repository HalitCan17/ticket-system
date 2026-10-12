package com.itsm.backend.ticket.sla;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.sla.SlaCalendarDtos.CalendarRequest;
import com.itsm.backend.ticket.sla.SlaCalendarDtos.CalendarResponse;
import com.itsm.backend.ticket.sla.SlaCalendarDtos.CalendarUpdateResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sla/calendar")
public class SlaCalendarController {

    private final SlaCalendarService calendar;

    public SlaCalendarController(SlaCalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    public CalendarResponse get(CurrentUser user) {
        return calendar.get(user);
    }

    @PutMapping
    public CalendarUpdateResponse update(CurrentUser user, @Valid @RequestBody CalendarRequest request) {
        return calendar.update(user, request);
    }
}
