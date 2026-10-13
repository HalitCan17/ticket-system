package com.itsm.backend.servicecatalog;

import java.time.Clock;
import java.time.Instant;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.InvalidFieldsException;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.servicecatalog.CatalogDtos.CreateRequest;
import com.itsm.backend.servicecatalog.CatalogDtos.DecisionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requests for catalog items. Customers see only their own requests (others are missing, not forbidden); staff see
 * all. Managers decide pending requests, never their own.
 */
@Service
public class ServiceRequestService {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestService.class);

    private final ServiceRequestRepository requests;
    private final CatalogItemService catalog;
    private final ApprovalFlow flow;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ServiceRequestService(ServiceRequestRepository requests, CatalogItemService catalog, ApprovalFlow flow,
            ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.catalog = catalog;
        this.flow = flow;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ServiceRequest create(CurrentUser user, CreateRequest body) {
        CatalogItem item = catalog.find(body.itemId());
        if (!item.isActive()) {
            // Customers do not know inactive items exist; staff learn why they cannot request it.
            if (!user.isStaff()) {
                throw new NotFoundException("CATALOG_ITEM_NOT_FOUND", "Catalog item " + item.getId() + " not found.");
            }
            throw new BadRequestException("ITEM_INACTIVE", "This catalog item cannot be requested at the moment.");
        }
        CatalogFormValidator.Result answers = CatalogFormValidator.checkAnswers(item.getFields(), body.answers());
        if (!answers.valid()) {
            throw new InvalidFieldsException("INVALID_ANSWERS", "Some answers are missing or not valid.",
                    answers.problems());
        }
        ServiceRequest request = requests.saveAndFlush(new ServiceRequest(item.getId(), user.id(), answers.answers(),
                item.isRequiresApproval(), clock.instant()));
        log.info("Service request {} for item {} created by {}", request.getId(), item.getId(), user.id());
        if (item.isRequiresApproval()) {
            events.publishEvent(new ServiceRequestEvents.ApprovalRequested(request.getId(), user.id()));
        }
        // Without approval the process fulfils the request (opens the ticket) before start returns.
        request.attachProcess(flow.start(request.getId(), item.isRequiresApproval()));
        return request;
    }

    @Transactional(readOnly = true)
    public Page<ServiceRequest> list(CurrentUser user, boolean mine, ServiceRequestStatus status, Pageable pageable) {
        if (!user.isStaff() || mine) {
            return status == null
                    ? requests.findByRequesterId(user.id(), pageable)
                    : requests.findByRequesterIdAndStatus(user.id(), status, pageable);
        }
        return status == null ? requests.findAll(pageable) : requests.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public ServiceRequest get(CurrentUser user, long id) {
        return findVisible(user, id);
    }

    @Transactional(readOnly = true)
    public long pendingApprovals(CurrentUser user) {
        requireManager(user);
        return requests.countByStatus(ServiceRequestStatus.PENDING_APPROVAL);
    }

    /** Only the requester withdraws, and only while it waits for approval. */
    @Transactional
    public ServiceRequest cancel(CurrentUser user, long id) {
        ServiceRequest request = findVisible(user, id);
        if (!request.getRequesterId().equals(user.id())) {
            throw new ForbiddenException("NOT_REQUESTER", "Only the requester can withdraw a service request.");
        }
        requirePending(request);
        request.cancel(clock.instant());
        flow.abort(request.getProcessInstanceId());
        log.info("Service request {} withdrawn by {}", id, user.id());
        return request;
    }

    @Transactional
    public ServiceRequest decide(CurrentUser user, long id, DecisionRequest body) {
        requireManager(user);
        ServiceRequest request = find(id);
        requirePending(request);
        if (request.getRequesterId().equals(user.id())) {
            throw new ForbiddenException("SELF_APPROVAL", "You cannot decide on your own request.");
        }
        String comment = body.comment() == null ? null : body.comment().strip();
        boolean approved = body.approved();
        if (!approved && (comment == null || comment.isEmpty())) {
            throw new BadRequestException("REASON_REQUIRED", "A rejection needs a reason.");
        }
        Instant now = clock.instant();
        request.recordDecision(user.id(), comment == null || comment.isEmpty() ? null : comment, now);
        requests.saveAndFlush(request);
        // The process continues synchronously: Fulfil opens the ticket, Reject marks the request (same transaction).
        flow.decide(user, id, approved);
        log.info("Service request {} {} by {}", id, approved ? "approved" : "rejected", user.id());
        return request;
    }

    public boolean canCancel(CurrentUser user, ServiceRequest request) {
        return request.isPending() && request.getRequesterId().equals(user.id());
    }

    public boolean canDecide(CurrentUser user, ServiceRequest request) {
        return request.isPending() && user.role() == Role.MANAGER && !request.getRequesterId().equals(user.id());
    }

    private ServiceRequest findVisible(CurrentUser user, long id) {
        ServiceRequest request = find(id);
        if (!user.isStaff() && !request.getRequesterId().equals(user.id())) {
            throw notFound(id);
        }
        return request;
    }

    private ServiceRequest find(long id) {
        return requests.findById(id).orElseThrow(() -> notFound(id));
    }

    private static void requirePending(ServiceRequest request) {
        if (!request.isPending()) {
            throw new ConflictException("NOT_PENDING", "This service request is no longer waiting for approval.");
        }
    }

    private static void requireManager(CurrentUser user) {
        if (user.role() != Role.MANAGER) {
            throw new ForbiddenException("MANAGER_ONLY", "Only managers decide on service requests.");
        }
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("SERVICE_REQUEST_NOT_FOUND", "Service request " + id + " not found.");
    }
}
