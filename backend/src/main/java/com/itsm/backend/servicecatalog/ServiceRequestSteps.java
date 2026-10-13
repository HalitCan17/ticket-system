package com.itsm.backend.servicecatalog;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.dto.CreateTicketRequest;
import com.itsm.backend.ticket.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The automatic steps of the {@code serviceRequest} process (processes/service-request.bpmn). The engine calls them
 * synchronously from whoever moved the process (request creation or a manager's decision), so they join that
 * transaction: if a step fails, the request change rolls back with it.
 */
@Component
public class ServiceRequestSteps {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestSteps.class);
    static final int DESCRIPTION_LIMIT = 10_000;

    private final ServiceRequestRepository requests;
    private final CatalogItemRepository items;
    private final TicketService tickets;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Only so the handler classes jBPM generates for the service tasks compile: they also declare a no-argument
     * constructor calling this one. Spring builds them with their {@code @Autowired} constructor and the bean below.
     */
    public ServiceRequestSteps() {
        throw new UnsupportedOperationException("ServiceRequestSteps is a Spring bean; inject it");
    }

    @Autowired
    public ServiceRequestSteps(ServiceRequestRepository requests, CatalogItemRepository items, TicketService tickets,
            ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.items = items;
        this.tickets = tickets;
        this.events = events;
        this.clock = clock;
    }

    /** Opens the ticket that delivers the request, on behalf of the requester. */
    @Transactional
    public void fulfil(Long requestId) {
        ServiceRequest request = find(requestId);
        CatalogItem item = items.findById(request.getItemId()).orElseThrow();
        // Only the id is used to open a ticket; the requester is the ticket's requester, as if they wrote it.
        Ticket ticket = tickets.create(new CurrentUser(request.getRequesterId(), Role.CUSTOMER),
                new CreateTicketRequest(item.getName(), description(request, item), item.getPriority(),
                        item.getCategory()));
        Instant now = clock.instant();
        boolean wasApproved = request.isPending();
        request.fulfilledBy(ticket.getId(), now);
        log.info("Service request {} fulfilled by ticket {}", requestId, ticket.getId());
        if (wasApproved) {
            events.publishEvent(new ServiceRequestEvents.Decided(requestId, request.getRequesterId(), true,
                    request.getApproverId()));
        }
    }

    @Transactional
    public void reject(Long requestId) {
        ServiceRequest request = find(requestId);
        request.reject(clock.instant());
        log.info("Service request {} rejected by {}", requestId, request.getApproverId());
        events.publishEvent(new ServiceRequestEvents.Decided(requestId, request.getRequesterId(), false,
                request.getApproverId()));
    }

    /** "Model: Standart\nGerekçe: ..." in the form's order, then where it came from. */
    static String description(ServiceRequest request, CatalogItem item) {
        var text = new StringBuilder();
        Map<String, String> answers = request.getOrderedAnswers();
        for (CatalogField field : item.getFields()) {
            String answer = answers.get(field.key());
            if (answer != null) {
                text.append(field.label()).append(": ").append(answer).append('\n');
            }
        }
        if (request.getDecisionComment() != null && !request.getDecisionComment().isBlank()) {
            text.append("Onay notu: ").append(request.getDecisionComment()).append('\n');
        }
        text.append('\n').append("Hizmet talebi #").append(request.getId()).append(" (").append(item.getName())
                .append(")");
        String result = text.toString().strip();
        return result.length() > DESCRIPTION_LIMIT ? result.substring(0, DESCRIPTION_LIMIT) : result;
    }

    private ServiceRequest find(Long id) {
        return requests.findById(id).orElseThrow(
                () -> new NotFoundException("SERVICE_REQUEST_NOT_FOUND", "Service request " + id + " not found."));
    }
}
