package com.itsm.backend.ticket.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.itsm.backend.common.web.PageResponse;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;
import com.itsm.backend.ticket.dto.CreateTicketRequest;
import com.itsm.backend.ticket.dto.StatusHistoryResponse;
import com.itsm.backend.ticket.dto.TicketResponse;
import com.itsm.backend.ticket.dto.TransitionRequest;
import com.itsm.backend.ticket.service.TicketResponseAssembler;
import com.itsm.backend.ticket.service.TicketService;
import com.itsm.backend.user.UserDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final TicketResponseAssembler responses;
    private final UserDirectory userDirectory;

    public TicketController(TicketService ticketService, TicketResponseAssembler responses,
            UserDirectory userDirectory) {
        this.ticketService = ticketService;
        this.responses = responses;
        this.userDirectory = userDirectory;
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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.toPage(ticketService.list(user, status, PageRequest.of(page, size, NEWEST_FIRST)), user);
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

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(CurrentUser user, @PathVariable long id) {
        List<TicketStatusHistory> entries = ticketService.history(user, id);
        Map<String, String> names = userDirectory.displayNames(
                entries.stream().map(TicketStatusHistory::getChangedBy).toList());
        return entries.stream().map(entry -> StatusHistoryResponse.from(entry, names)).toList();
    }
}
