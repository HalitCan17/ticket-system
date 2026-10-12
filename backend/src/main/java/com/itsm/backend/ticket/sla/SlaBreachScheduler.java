package com.itsm.backend.ticket.sla;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically runs {@link SlaBreachService#run} ({@code itsm.sla.breach-check-interval}, default 60s). */
@Component
@ConditionalOnProperty(prefix = "itsm.sla", name = "breach-check-enabled", havingValue = "true", matchIfMissing = true)
public class SlaBreachScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachScheduler.class);

    private final SlaBreachService breachService;
    private final Clock clock;

    public SlaBreachScheduler(SlaBreachService breachService, Clock clock) {
        this.breachService = breachService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${itsm.sla.breach-check-interval:60s}",
            initialDelayString = "${itsm.sla.breach-check-interval:60s}")
    public void run() {
        SlaBreachService.Result result = breachService.run(clock.instant());
        if (result.any()) {
            log.info("SLA check: {} warned, {} late first response(s), {} escalated", result.warned(),
                    result.firstResponseBreaches(), result.escalated());
        }
    }
}
