package com.itsm.backend.ticket.domain;

import java.util.Objects;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;

/**
 * Who may change a ticket's assignee. Pure Java like {@link TicketWorkflow}; see docs/adr/0011-work-queue-and-assignment.md.
 *
 * <ul>
 *   <li>MANAGER: any ticket, to any staff member or unassigned.</li>
 *   <li>AGENT: claims an unassigned ticket for themselves; hands over or releases a ticket assigned to them.
 *       Never takes a ticket assigned to someone else.</li>
 *   <li>CUSTOMER: never.</li>
 * </ul>
 */
public final class TicketAssignment {

    private TicketAssignment() {
    }

    /**
     * @param newAssigneeId      null to unassign
     * @param newAssigneeIsStaff whether {@code newAssigneeId} is a known AGENT/MANAGER (ignored when null)
     * @throws ForbiddenException  if the actor may not make this change ({@code ASSIGNMENT_NOT_ALLOWED})
     * @throws BadRequestException if the new assignee is not a staff member ({@code ASSIGNEE_NOT_STAFF})
     */
    public static void validate(CurrentUser actor, String currentAssigneeId, String newAssigneeId,
            boolean newAssigneeIsStaff) {
        if (!actor.isStaff()) {
            throw notAllowed();
        }
        if (newAssigneeId != null && !newAssigneeIsStaff) {
            throw new BadRequestException("ASSIGNEE_NOT_STAFF", "Tickets can only be assigned to agents or managers.");
        }
        if (actor.role() == Role.MANAGER) {
            return;
        }
        boolean claim = currentAssigneeId == null && actor.id().equals(newAssigneeId);
        boolean ownTicket = actor.id().equals(currentAssigneeId);
        if (!claim && !ownTicket) {
            throw notAllowed();
        }
    }

    /** The caller may take the (unassigned) ticket for themselves. */
    public static boolean canClaim(CurrentUser actor, String currentAssigneeId) {
        return actor.isStaff() && currentAssigneeId == null;
    }

    /** The caller may give the ticket to someone else or unassign it. */
    public static boolean canReassign(CurrentUser actor, String currentAssigneeId) {
        return actor.role() == Role.MANAGER
                || (actor.role() == Role.AGENT && Objects.equals(actor.id(), currentAssigneeId));
    }

    private static ForbiddenException notAllowed() {
        return new ForbiddenException("ASSIGNMENT_NOT_ALLOWED", "You are not allowed to change this ticket's assignee.");
    }
}
