package com.itsm.backend.common.time;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of "now" so time-dependent rules (SLA, auto-close) stay testable. */
@Configuration
public class ClockConfig {

    /**
     * Ticks in microseconds, the precision PostgreSQL stores. Otherwise a response built right after a
     * write could show more digits (e.g. Windows' 100 ns clock) than the same value read back later.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
