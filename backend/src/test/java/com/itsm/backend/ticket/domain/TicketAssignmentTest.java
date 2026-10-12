package com.itsm.backend.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import com.itsm.backend.common.error.ApiException;
import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TicketAssignmentTest {

    static final CurrentUser AGENT = new CurrentUser("agent1", Role.AGENT);
    static final CurrentUser OTHER_AGENT = new CurrentUser("agent2", Role.AGENT);
    static final CurrentUser MANAGER = new CurrentUser("manager1", Role.MANAGER);
    static final CurrentUser CUSTOMER = new CurrentUser("cust1", Role.CUSTOMER);

    /** actor, current assignee, new assignee, allowed. Every new assignee here is a staff member. */
    static Stream<Arguments> rules() {
        return Stream.of(
                // Agent: claim unassigned for self only
                Arguments.of(AGENT, null, "agent1", true),
                Arguments.of(AGENT, null, "agent2", false),
                // Agent: own ticket can be handed over or released
                Arguments.of(AGENT, "agent1", "agent2", true),
                Arguments.of(AGENT, "agent1", "manager1", true),
                Arguments.of(AGENT, "agent1", null, true),
                // Agent: someone else's ticket is off limits
                Arguments.of(AGENT, "agent2", "agent1", false),
                Arguments.of(AGENT, "agent2", null, false),
                // Manager: anything
                Arguments.of(MANAGER, null, "agent2", true),
                Arguments.of(MANAGER, "agent1", "agent2", true),
                Arguments.of(MANAGER, "agent1", null, true),
                Arguments.of(MANAGER, null, "manager1", true),
                // Customer: nothing
                Arguments.of(CUSTOMER, null, "agent1", false),
                Arguments.of(CUSTOMER, "agent1", null, false));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}: {3}")
    @MethodSource("rules")
    void followsTheRules(CurrentUser actor, String current, String next, boolean allowed) {
        if (allowed) {
            assertThatCode(() -> TicketAssignment.validate(actor, current, next, true)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> TicketAssignment.validate(actor, current, next, true))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo("ASSIGNMENT_NOT_ALLOWED");
        }
    }

    @Test
    void onlyStaffCanBeAssigned() {
        assertThatThrownBy(() -> TicketAssignment.validate(MANAGER, null, "cust1", false))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("ASSIGNEE_NOT_STAFF");
        // Unassigning needs no staff check.
        assertThatCode(() -> TicketAssignment.validate(MANAGER, "agent1", null, false)).doesNotThrowAnyException();
    }

    @Test
    void flagsForTheUi() {
        assertThat(TicketAssignment.canClaim(AGENT, null)).isTrue();
        assertThat(TicketAssignment.canClaim(AGENT, "agent2")).isFalse();
        assertThat(TicketAssignment.canClaim(CUSTOMER, null)).isFalse();

        assertThat(TicketAssignment.canReassign(AGENT, "agent1")).isTrue();
        assertThat(TicketAssignment.canReassign(OTHER_AGENT, "agent1")).isFalse();
        assertThat(TicketAssignment.canReassign(AGENT, null)).isFalse();
        assertThat(TicketAssignment.canReassign(MANAGER, null)).isTrue();
        assertThat(TicketAssignment.canReassign(CUSTOMER, "agent1")).isFalse();
    }
}
