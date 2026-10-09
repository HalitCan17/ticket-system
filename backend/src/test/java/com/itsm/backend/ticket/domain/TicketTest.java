package com.itsm.backend.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.junit.jupiter.api.Test;

class TicketTest {

    static final CurrentUser CUSTOMER = new CurrentUser("cust1", Role.CUSTOMER);
    static final CurrentUser AGENT = new CurrentUser("agent1", Role.AGENT);
    static final Instant T0 = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void opensAsNew() {
        Ticket ticket = newTicket();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(ticket.getRequesterId()).isEqualTo("cust1");
        assertThat(ticket.getAssigneeId()).isNull();
        assertThat(ticket.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void startingAssignsUnassignedTicketToActor() {
        Ticket ticket = newTicket();

        TicketStatus previous = ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0.plusSeconds(60));

        assertThat(previous).isEqualTo(TicketStatus.NEW);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAssigneeId()).isEqualTo("agent1");
        assertThat(ticket.getUpdatedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void resolveObjectAndClose() {
        Ticket ticket = newTicket();
        ticket.transitionTo(TicketStatus.IN_PROGRESS, AGENT, null, T0);

        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "  Restarted the service  ", T0.plusSeconds(10));
        assertThat(ticket.getResolutionNote()).isEqualTo("Restarted the service");
        assertThat(ticket.getResolvedAt()).isEqualTo(T0.plusSeconds(10));

        ticket.transitionTo(TicketStatus.IN_PROGRESS, CUSTOMER, "Still broken", T0.plusSeconds(20));
        assertThat(ticket.getResolutionNote()).isNull();
        assertThat(ticket.getResolvedAt()).isNull();

        ticket.transitionTo(TicketStatus.RESOLVED, AGENT, "Replaced the disk", T0.plusSeconds(30));
        ticket.transitionTo(TicketStatus.CLOSED, CUSTOMER, null, T0.plusSeconds(40));
        assertThat(ticket.isClosed()).isTrue();
        assertThat(ticket.getClosedAt()).isEqualTo(T0.plusSeconds(40));
        assertThat(ticket.getResolutionNote()).isEqualTo("Replaced the disk");
    }

    private static Ticket newTicket() {
        return Ticket.open("VPN down", "Cannot connect", TicketPriority.HIGH, "cust1", T0);
    }
}
