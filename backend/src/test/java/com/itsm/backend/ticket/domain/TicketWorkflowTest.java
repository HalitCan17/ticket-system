package com.itsm.backend.ticket.domain;

import static com.itsm.backend.ticket.domain.TicketStatus.CLOSED;
import static com.itsm.backend.ticket.domain.TicketStatus.IN_PROGRESS;
import static com.itsm.backend.ticket.domain.TicketStatus.NEW;
import static com.itsm.backend.ticket.domain.TicketStatus.RESOLVED;
import static com.itsm.backend.ticket.domain.TicketStatus.WAITING_FOR_CUSTOMER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.itsm.backend.common.error.ApiException;
import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TicketWorkflowTest {

    static final String REQUESTER_ID = "cust1";

    enum Actor {
        REQUESTER(new CurrentUser(REQUESTER_ID, Role.CUSTOMER)),
        AGENT(new CurrentUser("agent1", Role.AGENT)),
        MANAGER(new CurrentUser("manager1", Role.MANAGER));

        final CurrentUser user;

        Actor(CurrentUser user) {
            this.user = user;
        }
    }

    /** The business rules specification, written independently of the production rule table. */
    static final Map<List<TicketStatus>, Set<Actor>> ALLOWED = Map.of(
            List.of(NEW, IN_PROGRESS), EnumSet.of(Actor.AGENT, Actor.MANAGER),
            List.of(IN_PROGRESS, WAITING_FOR_CUSTOMER), EnumSet.of(Actor.AGENT, Actor.MANAGER),
            List.of(WAITING_FOR_CUSTOMER, IN_PROGRESS), EnumSet.of(Actor.AGENT, Actor.MANAGER),
            List.of(IN_PROGRESS, RESOLVED), EnumSet.of(Actor.AGENT, Actor.MANAGER),
            List.of(RESOLVED, IN_PROGRESS), EnumSet.of(Actor.REQUESTER, Actor.MANAGER),
            List.of(RESOLVED, CLOSED), EnumSet.of(Actor.REQUESTER, Actor.MANAGER));

    static Stream<Arguments> everyTransitionAndActor() {
        List<Arguments> args = new ArrayList<>();
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                for (Actor actor : Actor.values()) {
                    args.add(Arguments.of(from, to, actor));
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} by {2}")
    @MethodSource("everyTransitionAndActor")
    void matchesSpecification(TicketStatus from, TicketStatus to, Actor actor) {
        Set<Actor> allowedActors = ALLOWED.get(List.of(from, to));

        if (allowedActors == null) {
            assertThatThrownBy(() -> validate(from, to, actor))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
        } else if (!allowedActors.contains(actor)) {
            assertThatThrownBy(() -> validate(from, to, actor))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo("TRANSITION_NOT_ALLOWED");
        } else {
            assertThatCode(() -> validate(from, to, actor)).doesNotThrowAnyException();
        }
    }

    @Test
    void resolvingRequiresResolutionNote() {
        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> TicketWorkflow.validate(IN_PROGRESS, RESOLVED, Actor.AGENT.user, REQUESTER_ID, blank))
                    .isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo(TicketWorkflow.RESOLUTION_NOTE_REQUIRED);
        }
    }

    @Test
    void objectionRequiresReason() {
        assertThatThrownBy(() -> TicketWorkflow.validate(RESOLVED, IN_PROGRESS, Actor.REQUESTER.user, REQUESTER_ID, " "))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(TicketWorkflow.REOPEN_REASON_REQUIRED);
    }

    @Test
    void anotherCustomerCannotCloseOrObject() {
        CurrentUser otherCustomer = new CurrentUser("cust2", Role.CUSTOMER);

        assertThatThrownBy(() -> TicketWorkflow.validate(RESOLVED, CLOSED, otherCustomer, REQUESTER_ID, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> TicketWorkflow.validate(RESOLVED, IN_PROGRESS, otherCustomer, REQUESTER_ID, "why"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void allowedTargetsFollowRules() {
        assertThat(TicketWorkflow.allowedTargets(RESOLVED, Actor.REQUESTER.user, REQUESTER_ID))
                .containsExactlyInAnyOrder(IN_PROGRESS, CLOSED);
        assertThat(TicketWorkflow.allowedTargets(RESOLVED, Actor.AGENT.user, REQUESTER_ID)).isEmpty();
        assertThat(TicketWorkflow.allowedTargets(IN_PROGRESS, Actor.AGENT.user, REQUESTER_ID))
                .containsExactlyInAnyOrder(WAITING_FOR_CUSTOMER, RESOLVED);
        assertThat(TicketWorkflow.allowedTargets(CLOSED, Actor.MANAGER.user, REQUESTER_ID)).isEmpty();
    }

    private static void validate(TicketStatus from, TicketStatus to, Actor actor) {
        TicketWorkflow.validate(from, to, actor.user, REQUESTER_ID, "a note");
    }
}
