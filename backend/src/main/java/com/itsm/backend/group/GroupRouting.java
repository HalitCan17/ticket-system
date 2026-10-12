package com.itsm.backend.group;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketActivity;
import com.itsm.backend.ticket.domain.TicketActivityType;
import com.itsm.backend.ticket.event.TicketAssigneeChangedEvent;
import com.itsm.backend.ticket.repository.TicketActivityRepository;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.user.UserDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Puts tickets into the group of their category and, if the group says so, assigns them to a member.
 * Runs inside the caller's transaction (ticket creation or a staff edit).
 */
@Component
public class GroupRouting {

    private static final Logger log = LoggerFactory.getLogger(GroupRouting.class);

    private final GroupDirectory directory;
    private final SupportGroupRepository groups;
    private final TicketActivityRepository activities;
    private final TicketRepository tickets;
    private final ApplicationEventPublisher events;

    public GroupRouting(GroupDirectory directory, SupportGroupRepository groups, TicketActivityRepository activities,
            TicketRepository tickets, ApplicationEventPublisher events) {
        this.directory = directory;
        this.groups = groups;
        this.activities = activities;
        this.tickets = tickets;
        this.events = events;
    }

    /** A new ticket joins its category's group (not recorded as an activity: it is where it started). */
    public void routeNewTicket(Ticket ticket, Instant now) {
        Long groupId = directory.groupFor(ticket.getCategory());
        if (groupId != null) {
            ticket.moveToGroup(groupId, now);
            assignWithinGroup(ticket, now);
        }
    }

    /** After a category change the ticket follows the new category's route, if there is one. */
    public void followCategory(Ticket ticket, String actorId, Instant now) {
        Long groupId = directory.groupFor(ticket.getCategory());
        if (groupId != null) {
            moveTicket(ticket, groupId, actorId, now);
        }
    }

    /**
     * Moves the ticket to another group (null = none), records the change and assigns within the new group
     * if the ticket has no assignee.
     *
     * @throws BadRequestException if the group does not exist ({@code GROUP_NOT_FOUND})
     */
    public void moveTicket(Ticket ticket, Long groupId, String actorId, Instant now) {
        if (Objects.equals(groupId, ticket.getGroupId())) {
            return;
        }
        if (groupId != null && !groups.existsById(groupId)) {
            throw new BadRequestException("GROUP_NOT_FOUND", "Support group " + groupId + " does not exist.");
        }
        Long previous = ticket.moveToGroup(groupId, now);
        activities.save(new TicketActivity(ticket.getId(), TicketActivityType.GROUP_CHANGED, asText(previous),
                asText(groupId), null, actorId, now));
        log.info("Ticket {} group {} -> {} by {}", ticket.getId(), previous, groupId, actorId);
        assignWithinGroup(ticket, now);
    }

    /**
     * A member left the group: their open tickets of this group are taken from them and assigned again following
     * the group's mode (or wait unassigned in the group's queue). Resolved and closed tickets stay as they are.
     * Call after the membership change was flushed.
     *
     * @return number of tickets taken from the member
     */
    public int releaseMember(long groupId, String userId, String actorId, Instant now) {
        List<Ticket> held = tickets.findOpenInGroupAssignedTo(groupId, userId);
        for (Ticket ticket : held) {
            ticket.releaseAssignee(now);
            activities.save(new TicketActivity(ticket.getId(), TicketActivityType.ASSIGNEE_CHANGED, userId, null,
                    null, actorId, now));
            events.publishEvent(new TicketAssigneeChangedEvent(ticket, userId, actorId));
            assignWithinGroup(ticket, now);
        }
        if (!held.isEmpty()) {
            log.info("{} open ticket(s) of group {} released from {} by {}", held.size(), groupId, userId, actorId);
        }
        return held.size();
    }

    private void assignWithinGroup(Ticket ticket, Instant now) {
        if (ticket.getAssigneeId() != null || ticket.isClosed() || ticket.getGroupId() == null) {
            return;
        }
        SupportGroup group = groups.findByIdForUpdate(ticket.getGroupId()).orElse(null);
        if (group == null || group.getAssignmentMode() == AssignmentMode.NONE) {
            return;
        }
        List<String> members = directory.assignableMembers(group.getId());
        Optional<String> chosen = AssignmentPicker.pick(group.getAssignmentMode(), members, group.getLastAssignedId(),
                group.getAssignmentMode() == AssignmentMode.LEAST_LOADED ? directory.openWork(members) : Map.of());
        if (chosen.isEmpty() || !ticket.assignAutomatically(chosen.get(), now)) {
            return;
        }
        group.recordAutomaticAssignment(chosen.get());
        activities.save(new TicketActivity(ticket.getId(), TicketActivityType.ASSIGNEE_CHANGED, null, chosen.get(),
                null, UserDirectory.SYSTEM_USER_ID, now));
        events.publishEvent(new TicketAssigneeChangedEvent(ticket, null, UserDirectory.SYSTEM_USER_ID));
        log.info("Ticket {} assigned to {} by group {} ({})", ticket.getId(), chosen.get(), group.getId(),
                group.getAssignmentMode());
    }

    private static String asText(Long id) {
        return id == null ? null : id.toString();
    }
}
