package com.itsm.backend.ticket.controller;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.web.PageResponse;
import com.itsm.backend.group.GroupDirectory;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketActivity;
import com.itsm.backend.ticket.domain.TicketActivityType;
import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketScope;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;
import com.itsm.backend.ticket.dto.ActivityResponse;
import com.itsm.backend.ticket.dto.AssignTicketRequest;
import com.itsm.backend.ticket.dto.CreateTicketRequest;
import com.itsm.backend.ticket.dto.RateTicketRequest;
import com.itsm.backend.ticket.dto.StatusHistoryResponse;
import com.itsm.backend.ticket.dto.TicketResponse;
import com.itsm.backend.ticket.dto.TransitionRequest;
import com.itsm.backend.ticket.dto.UpdateTicketRequest;
import com.itsm.backend.ticket.service.TicketManagementService;
import com.itsm.backend.ticket.service.TicketResponseAssembler;
import com.itsm.backend.ticket.service.TicketSearch;
import com.itsm.backend.ticket.service.TicketService;
import com.itsm.backend.user.UserDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TicketService ticketService;
    private final TicketManagementService management;
    private final TicketResponseAssembler responses;
    private final UserDirectory userDirectory;
    private final GroupDirectory groupDirectory;

    public TicketController(TicketService ticketService, TicketManagementService management,
            TicketResponseAssembler responses, UserDirectory userDirectory, GroupDirectory groupDirectory) {
        this.ticketService = ticketService;
        this.management = management;
        this.responses = responses;
        this.userDirectory = userDirectory;
        this.groupDirectory = groupDirectory;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(CurrentUser user, @Valid @RequestBody CreateTicketRequest request) {
        Ticket ticket = ticketService.create(user, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(ticket.getId()).toUri();
        return ResponseEntity.created(location).body(responses.toResponse(ticket, user));
    }

    @GetMapping
    public PageResponse<TicketResponse> list(
            CurrentUser user,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketScope scope,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 64) String assignee,
            @RequestParam(required = false) TicketSort sort,
            @RequestParam(required = false) @Size(max = 20) String group,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // assignee: "me", "unassigned" or a user id (staff work queue)
        boolean unassigned = "unassigned".equalsIgnoreCase(assignee);
        String assigneeId = assignee == null || assignee.isBlank() || unassigned ? null
                : "me".equalsIgnoreCase(assignee) ? user.id() : assignee;
        boolean bySla = sort == TicketSort.SLA;
        TicketSearch search = new TicketSearch(q, scope, status, category, assigneeId, unassigned, bySla,
                groupFilter(user, group));
        PageRequest pageable = bySla ? PageRequest.of(page, size) : PageRequest.of(page, size, NEWEST_FIRST);
        return responses.toPage(ticketService.list(user, search, pageable), user);
    }

    /** group: "mine" (the caller's groups) or a group id; null = no group filter. Staff only, see TicketSpecifications. */
    private List<Long> groupFilter(CurrentUser user, String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        if ("mine".equalsIgnoreCase(group)) {
            return groupDirectory.groupIdsOf(user.id());
        }
        try {
            return List.of(Long.parseLong(group.strip()));
        } catch (NumberFormatException e) {
            throw new BadRequestException("INVALID_GROUP_FILTER", "group must be 'mine' or a group id.");
        }
    }

    /** List order: newest first (default) or most urgent SLA first. */
    public enum TicketSort {
        NEWEST, SLA
    }

    @GetMapping("/{id}")
    public TicketResponse get(CurrentUser user, @PathVariable long id) {
        return responses.toResponse(ticketService.get(user, id), user);
    }

    @PostMapping("/{id}/transitions")
    public TicketResponse transition(CurrentUser user, @PathVariable long id,
            @Valid @RequestBody TransitionRequest request) {
        return responses.toResponse(ticketService.transition(user, id, request), user);
    }

    @PutMapping("/{id}/assignee")
    public TicketResponse assign(CurrentUser user, @PathVariable long id,
            @Valid @RequestBody AssignTicketRequest request) {
        return responses.toResponse(management.assign(user, id, request.assigneeId()), user);
    }

    @PatchMapping("/{id}")
    public TicketResponse update(CurrentUser user, @PathVariable long id,
            @Valid @RequestBody UpdateTicketRequest request) {
        return responses.toResponse(management.update(user, id, request), user);
    }

    @GetMapping("/{id}/activities")
    public List<ActivityResponse> activities(CurrentUser user, @PathVariable long id) {
        List<TicketActivity> entries = management.activities(user, id);
        List<String> ids = new ArrayList<>();
        for (TicketActivity entry : entries) {
            ids.add(entry.getActorId());
            if (entry.getType() == TicketActivityType.ASSIGNEE_CHANGED) {
                ids.add(entry.getOldValue());
                ids.add(entry.getNewValue());
            }
        }
        Map<String, String> names = userDirectory.displayNames(ids);
        Map<Long, String> groupNames = groupDirectory.names(entries.stream()
                .filter(entry -> entry.getType() == TicketActivityType.GROUP_CHANGED)
                .flatMap(entry -> Stream.of(entry.getOldValue(), entry.getNewValue()))
                .filter(Objects::nonNull).map(Long::valueOf).toList());
        return entries.stream().map(entry -> ActivityResponse.from(entry, names, groupNames)).toList();
    }

    @PostMapping("/{id}/rating")
    public TicketResponse rate(CurrentUser user, @PathVariable long id,
            @Valid @RequestBody RateTicketRequest request) {
        return responses.toResponse(ticketService.rate(user, id, request), user);
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(CurrentUser user, @PathVariable long id) {
        List<TicketStatusHistory> entries = ticketService.history(user, id);
        Map<String, String> names = userDirectory.displayNames(
                entries.stream().map(TicketStatusHistory::getChangedBy).toList());
        return entries.stream().map(entry -> StatusHistoryResponse.from(entry, names)).toList();
    }
}
