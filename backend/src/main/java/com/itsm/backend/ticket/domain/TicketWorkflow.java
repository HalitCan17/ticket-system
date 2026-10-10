package com.itsm.backend.ticket.domain;

import static com.itsm.backend.ticket.domain.TicketStatus.CLOSED;
import static com.itsm.backend.ticket.domain.TicketStatus.IN_PROGRESS;
import static com.itsm.backend.ticket.domain.TicketStatus.NEW;
import static com.itsm.backend.ticket.domain.TicketStatus.RESOLVED;
import static com.itsm.backend.ticket.domain.TicketStatus.WAITING_FOR_CUSTOMER;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;

/**
 * Ticket status rules: which transitions exist, who may perform them and which require a note.
 * Pure Java on purpose (see docs/adr/0002-ticket-status-flow-in-service-layer.md).
 *
 * <pre>
 * NEW → IN_PROGRESS ⇄ WAITING_FOR_CUSTOMER
 * IN_PROGRESS → RESOLVED (resolution note required)
 * RESOLVED → IN_PROGRESS (requester's objection, reason required) | RESOLVED → CLOSED
 * </pre>
 */
public final class TicketWorkflow {

    /** Who is acting, relative to a specific ticket. */
    enum Party {
        AGENT, MANAGER, REQUESTER
    }

    private record Transition(TicketStatus from, TicketStatus to) {
    }

    private record Rule(Set<Party> parties, String requiredNoteCode) {
    }

    public static final String RESOLUTION_NOTE_REQUIRED = "RESOLUTION_NOTE_REQUIRED";
    public static final String REOPEN_REASON_REQUIRED = "REOPEN_REASON_REQUIRED";

    private static final Set<Party> STAFF = EnumSet.of(Party.AGENT, Party.MANAGER);
    private static final Set<Party> REQUESTER_OR_MANAGER = EnumSet.of(Party.REQUESTER, Party.MANAGER);

    private static final Map<Transition, Rule> RULES = Map.of(
            new Transition(NEW, IN_PROGRESS), new Rule(STAFF, null),
            new Transition(IN_PROGRESS, WAITING_FOR_CUSTOMER), new Rule(STAFF, null),
            new Transition(WAITING_FOR_CUSTOMER, IN_PROGRESS), new Rule(STAFF, null),
            new Transition(IN_PROGRESS, RESOLVED), new Rule(STAFF, RESOLUTION_NOTE_REQUIRED),
            new Transition(RESOLVED, IN_PROGRESS), new Rule(REQUESTER_OR_MANAGER, REOPEN_REASON_REQUIRED),
            new Transition(RESOLVED, CLOSED), new Rule(REQUESTER_OR_MANAGER, null));

    private TicketWorkflow() {
    }

    /**
     * @throws ConflictException   if the transition does not exist ({@code INVALID_STATUS_TRANSITION})
     * @throws ForbiddenException  if the actor may not perform it ({@code TRANSITION_NOT_ALLOWED})
     * @throws BadRequestException if a required note is blank
     */
    public static void validate(TicketStatus from, TicketStatus to, CurrentUser actor, String requesterId, String note) {
        Rule rule = RULES.get(new Transition(from, to));
        if (rule == null) {
            throw new ConflictException("INVALID_STATUS_TRANSITION",
                    "Ticket cannot move from %s to %s.".formatted(from, to));
        }
        if (!canAct(rule, actor, requesterId)) {
            throw new ForbiddenException("TRANSITION_NOT_ALLOWED",
                    "You are not allowed to move this ticket from %s to %s.".formatted(from, to));
        }
        if (rule.requiredNoteCode() != null && (note == null || note.isBlank())) {
            throw new BadRequestException(rule.requiredNoteCode(),
                    "A note is required to move a ticket from %s to %s.".formatted(from, to));
        }
    }

    /**
     * The only transition the system performs on its own: RESOLVED → CLOSED when the customer
     * did not respond in time (docs/adr/0005-sla-and-auto-close.md).
     *
     * @throws ConflictException if the ticket is not RESOLVED
     */
    public static void validateSystemClose(TicketStatus from) {
        if (from != RESOLVED) {
            throw new ConflictException("INVALID_STATUS_TRANSITION",
                    "Only RESOLVED tickets can be closed automatically, not %s.".formatted(from));
        }
    }

    /** Target statuses the actor may move the ticket to from {@code from} (notes not considered). */
    public static Set<TicketStatus> allowedTargets(TicketStatus from, CurrentUser actor, String requesterId) {
        return Arrays.stream(TicketStatus.values())
                .filter(to -> {
                    Rule rule = RULES.get(new Transition(from, to));
                    return rule != null && canAct(rule, actor, requesterId);
                })
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TicketStatus.class)));
    }

    private static boolean canAct(Rule rule, CurrentUser actor, String requesterId) {
        return partiesOf(actor, requesterId).stream().anyMatch(rule.parties()::contains);
    }

    static Set<Party> partiesOf(CurrentUser actor, String requesterId) {
        Set<Party> parties = EnumSet.noneOf(Party.class);
        if (actor.role() == Role.AGENT) {
            parties.add(Party.AGENT);
        }
        if (actor.role() == Role.MANAGER) {
            parties.add(Party.MANAGER);
        }
        if (actor.id().equals(requesterId)) {
            parties.add(Party.REQUESTER);
        }
        return parties;
    }
}
