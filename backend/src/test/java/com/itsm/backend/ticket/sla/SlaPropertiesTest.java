package com.itsm.backend.ticket.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketPriority;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SlaPropertiesTest {

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        SlaProperties properties = new SlaProperties(null);

        assertThat(properties.targetFor(TicketPriority.CRITICAL)).isEqualTo(Duration.ofHours(4));
        assertThat(properties.targetFor(TicketPriority.HIGH)).isEqualTo(Duration.ofHours(8));
        assertThat(properties.targetFor(TicketPriority.MEDIUM)).isEqualTo(Duration.ofHours(24));
        assertThat(properties.targetFor(TicketPriority.LOW)).isEqualTo(Duration.ofHours(72));
    }

    @Test
    void configuredTargetsOverrideDefaultsPerPriority() {
        SlaProperties properties = bind(Map.of("itsm.sla.targets.HIGH", "2h", "itsm.sla.targets.LOW", "5d"));

        assertThat(properties.targetFor(TicketPriority.HIGH)).isEqualTo(Duration.ofHours(2));
        assertThat(properties.targetFor(TicketPriority.LOW)).isEqualTo(Duration.ofDays(5));
        assertThat(properties.targetFor(TicketPriority.CRITICAL)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void zeroTargetIsRejected() {
        assertThatThrownBy(() -> new SlaProperties(Map.of(TicketPriority.LOW, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SlaProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("itsm.sla", SlaProperties.class);
    }
}
