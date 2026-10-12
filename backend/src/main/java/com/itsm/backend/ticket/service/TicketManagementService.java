package com.itsm.backend.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.group.GroupRouting;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketActivity;
import com.itsm.backend.ticket.domain.TicketActivityType;
import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.dto.UpdateTicketRequest;
import com.itsm.backend.ticket.event.TicketAssigneeChangedEvent;
import com.itsm.backend.ticket.event.TicketPriorityChangedEvent;
import com.itsm.backend.ticket.repository.TicketActivityRepository;
import com.itsm.backend.ticket.sla.SlaProperties;
import com.itsm.backend.user.UserDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff work on tickets besides status changes: assignment, priority and category.
 * Every change is recorded in {@code ticket_activities}; see docs/adr/0011-work-queue-and-assignment.md.
 */
@Service
public class TicketManagementService {

    private static final Logger log = LoggerFactory.getLogger(TicketManagementService.class);

    private final TicketService tickets;
    private final TicketActivityRepository activities;
    private final UserDirectory userDirectory;
    private final SlaProperties sla;
    private final GroupRouting routing;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TicketManagementService(TicketService tickets, TicketActivityRepository activities,
            UserDirectory userDirectory, SlaProperties sla, GroupRouting routing, ApplicationEventPublisher events,
            Clock clock) {
        this.tickets = tickets;
        this.activities = activities;
        this.userDirectory = userDirectory;
        this.sla = sla;
        this.routing = routing;
        this.events = events;
        this.clock = clock;
    }

    /** Assigning the current assignee again changes nothing and records nothing. */
    @Transactional
    public Ticket assign(CurrentUser user, long id, String assigneeId) {
        Ticket ticket = tickets.getVisibleTicket(user, id);
        requireStaff(user, "ASSIGNMENT_NOT_ALLOWED");
        String target = assigneeId == null || assigneeId.isBlank() ? null : assigneeId.strip();
        if (Objects.equals(target, ticket.getAssigneeId())) {
            return ticket;
        }
        Instant now = clock.instant();
        boolean targetIsStaff = target != null && userDirectory.isStaff(target);
        String previous = ticket.assignTo(target, targetIsStaff, user, now);
        activities.save(new TicketActivity(ticket.getId(), TicketActivityType.ASSIGNEE_CHANGED, previous, target, null,
                user.id(), now));
        events.publishEvent(new TicketAssigneeChangedEvent(ticket, previous, user.id()));
        log.info("Ticket {} assignee {} -> {} by {}", ticket.getId(), previous, target, user.id());
        return ticket;
    }

    /** Changes priority and/or category; unchanged values are ignored. */
    @Transactional
    public Ticket update(CurrentUser user, long id, UpdateTicketRequest request) {
        boolean changesGroup = request.groupId() != null || Boolean.TRUE.equals(request.removeGroup());
        if (request.priority() == null && request.category() == null && !changesGroup) {
            throw new BadRequestException("NOTHING_TO_UPDATE", "Give a priority, a category or a group to change.");
        }
        Ticket ticket = tickets.getVisibleTicket(user, id);
        requireStaff(user, "TICKET_EDIT_NOT_ALLOWED");
        Instant now = clock.instant();
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();

        if (request.priority() != null && request.priority() != ticket.getPriority()) {
            TicketPriority previous = ticket.changePriority(request.priority(), sla.targetFor(request.priority()),
                    sla.firstResponseFor(request.priority()), reason, user, now);
            activities.save(new TicketActivity(ticket.getId(), TicketActivityType.PRIORITY_CHANGED, previous.name(),
                    request.priority().name(), reason, user.id(), now));
            events.publishEvent(new TicketPriorityChangedEvent(ticket, previous, user.id()));
            log.info("Ticket {} priority {} -> {} by {}", ticket.getId(), previous, request.priority(), user.id());
        }
        if (request.category() != null && request.category() != ticket.getCategory()) {
            TicketCategory previous = ticket.changeCategory(request.category(), user, now);
            activities.save(new TicketActivity(ticket.getId(), TicketActivityType.CATEGORY_CHANGED, previous.name(),
                    request.category().name(), null, user.id(), now));
            log.info("Ticket {} category {} -> {} by {}", ticket.getId(), previous, request.category(), user.id());
            routing.followCategory(ticket, user.id(), now);
        }
        if (changesGroup) {
            if (ticket.isClosed()) {
                throw new ConflictException("TICKET_CLOSED", "A closed ticket cannot be changed.");
            }
            // An explicit group wins over the category's route when both are given.
            routing.moveTicket(ticket, Boolean.TRUE.equals(request.removeGroup()) ? null : request.groupId(), user.id(), now);
        }
        return ticket;
    }

    /** Also rejects a no-op request from a customer, which the domain rules alone would let through. */
    private static void requireStaff(CurrentUser user, String code) {
        if (!user.isStaff()) {
            throw new ForbiddenException(code, "Only staff can do this.");
        }
    }

    /** Staff only: a customer gets 404 as if the log did not exist (reasons may be internal). */
    @Transactional(readOnly = true)
    public List<TicketActivity> activities(CurrentUser user, long id) {
        if (!user.isStaff()) {
            throw new NotFoundException("TICKET_NOT_FOUND", "Ticket " + id + " not found.");
        }
        tickets.getVisibleTicket(user, id);
        return activities.findByTicketIdOrderByCreatedAtAscIdAsc(id);
    }
}
