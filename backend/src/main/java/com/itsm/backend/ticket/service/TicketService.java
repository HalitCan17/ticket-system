package com.itsm.backend.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;
import com.itsm.backend.ticket.dto.CreateTicketRequest;
import com.itsm.backend.ticket.dto.RateTicketRequest;
import com.itsm.backend.ticket.dto.TransitionRequest;
import com.itsm.backend.ticket.event.TicketRatedEvent;
import com.itsm.backend.ticket.event.TicketStatusChangedEvent;
import com.itsm.backend.ticket.repository.TicketRepository;
import com.itsm.backend.ticket.repository.TicketSpecifications;
import com.itsm.backend.ticket.repository.TicketStatusHistoryRepository;
import com.itsm.backend.ticket.sla.SlaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository tickets;
    private final TicketStatusHistoryRepository history;
    private final SlaProperties sla;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TicketService(TicketRepository tickets, TicketStatusHistoryRepository history, SlaProperties sla,
            ApplicationEventPublisher events, Clock clock) {
        this.tickets = tickets;
        this.history = history;
        this.sla = sla;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Ticket create(CurrentUser user, CreateTicketRequest request) {
        Instant now = clock.instant();
        Ticket ticket = tickets.save(Ticket.open(request.title().strip(), request.description().strip(),
                request.priority(), request.category(), user.id(), sla.targetFor(request.priority()), now));
        history.save(new TicketStatusHistory(ticket.getId(), null, TicketStatus.NEW, user.id(), null, now));
        log.info("Ticket {} created by {} with priority {}", ticket.getId(), user.id(), ticket.getPriority());
        return ticket;
    }

    /** Customers see only their own tickets; staff see all. */
    @Transactional(readOnly = true)
    public Page<Ticket> list(CurrentUser user, TicketSearch search, Pageable pageable) {
        return tickets.findAll(TicketSpecifications.matching(search, user.isStaff() ? null : user.id()), pageable);
    }

    @Transactional(readOnly = true)
    public Ticket get(CurrentUser user, long id) {
        return getVisibleTicket(user, id);
    }

    @Transactional
    public Ticket transition(CurrentUser user, long id, TransitionRequest request) {
        Ticket ticket = getVisibleTicket(user, id);
        Instant now = clock.instant();
        String note = normalize(request.note());

        TicketStatus previous = ticket.transitionTo(request.targetStatus(), user, note, now);
        history.save(new TicketStatusHistory(ticket.getId(), previous, ticket.getStatus(), user.id(), note, now));
        events.publishEvent(new TicketStatusChangedEvent(ticket, previous, ticket.getStatus(), user.id()));
        log.info("Ticket {} moved {} -> {} by {}", ticket.getId(), previous, ticket.getStatus(), user.id());
        return ticket;
    }

    /** The requester rates a resolved ticket once; the assignee is notified. */
    @Transactional
    public Ticket rate(CurrentUser user, long id, RateTicketRequest request) {
        Ticket ticket = getVisibleTicket(user, id);
        ticket.rate(user, request.score(), request.comment(), clock.instant());
        events.publishEvent(new TicketRatedEvent(ticket, user.id()));
        log.info("Ticket {} rated {} by {}", ticket.getId(), request.score(), user.id());
        return ticket;
    }

    @Transactional(readOnly = true)
    public List<TicketStatusHistory> history(CurrentUser user, long id) {
        getVisibleTicket(user, id);
        return history.findByTicketIdOrderByChangedAtAscIdAsc(id);
    }

    /**
     * Loads a ticket the caller may see. A customer asking for someone else's ticket gets the same
     * 404 as for a missing one, so ticket ids of other customers are not disclosed.
     */
    public Ticket getVisibleTicket(CurrentUser user, long id) {
        return tickets.findById(id)
                .filter(ticket -> user.isStaff() || ticket.getRequesterId().equals(user.id()))
                .orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "Ticket " + id + " not found."));
    }

    private static String normalize(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }
}
