package com.itsm.backend.ticket.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically triggers {@link TicketAutoCloseService}. Assumes a single backend instance; with several
 * instances optimistic locking prevents double closing, a scheduler lock (e.g. ShedLock) would avoid the
 * duplicate work (docs/adr/0005-sla-and-auto-close.md).
 */
@Component
@ConditionalOnProperty(prefix = "itsm.auto-close", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AutoCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseScheduler.class);

    private final TicketAutoCloseService autoCloseService;
    private final Clock clock;

    public AutoCloseScheduler(TicketAutoCloseService autoCloseService, Clock clock) {
        this.autoCloseService = autoCloseService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${itsm.auto-close.poll-interval:60s}",
            initialDelayString = "${itsm.auto-close.poll-interval:60s}")
    public void run() {
        int closed = autoCloseService.closeStaleResolved(clock.instant());
        if (closed > 0) {
            log.info("Auto-closed {} resolved ticket(s)", closed);
        }
    }
}
