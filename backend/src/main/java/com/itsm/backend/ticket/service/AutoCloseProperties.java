package com.itsm.backend.ticket.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Auto-close of RESOLVED tickets the customer did not respond to ({@code itsm.auto-close.*}).
 *
 * @param enabled      whether the scheduler runs
 * @param after        how long a ticket stays RESOLVED before it is closed automatically
 * @param pollInterval delay between two scheduler runs
 */
@ConfigurationProperties("itsm.auto-close")
public record AutoCloseProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("7d") Duration after,
        @DefaultValue("60s") Duration pollInterval) {

    public AutoCloseProperties {
        if (after.isNegative() || after.isZero()) {
            throw new IllegalArgumentException("itsm.auto-close.after must be positive");
        }
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("itsm.auto-close.poll-interval must be positive");
        }
    }
}
