package com.itsm.backend.ticket.sla;

import java.util.concurrent.atomic.AtomicReference;

import com.itsm.backend.ticket.domain.BusinessCalendar;
import org.springframework.stereotype.Component;

/**
 * The calendar the SLA clocks use right now. Read from the database and cached briefly, so every request does not
 * query it; {@link BusinessCalendarService} invalidates the cache when a manager saves new settings.
 */
@Component
public class BusinessCalendarProvider {

    /** Other instances pick up a change at the latest after this long. */
    static final long TTL_NANOS = 30_000_000_000L;

    private record Cached(BusinessCalendar calendar, long loadedAtNanos) {
    }

    private final BusinessCalendarStore store;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public BusinessCalendarProvider(BusinessCalendarStore store) {
        this.store = store;
    }

    public BusinessCalendar current() {
        Cached cached = cache.get();
        long now = System.nanoTime();
        if (cached == null || now - cached.loadedAtNanos() > TTL_NANOS) {
            cached = new Cached(store.load().toCalendar(), now);
            cache.set(cached);
        }
        return cached.calendar();
    }

    /** The next {@link #current()} reads the settings again. */
    public void invalidate() {
        cache.set(null);
    }
}
