package com.itsm.backend.ticket.controller;

import java.net.URI;
import java.util.List;

import com.itsm.backend.common.web.PageResponse;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.dto.CreateTicketRequest;
import com.itsm.backend.ticket.dto.StatusHistoryResponse;
import com.itsm.backend.ticket.dto.TicketResponse;
import com.itsm.backend.ticket.dto.TransitionRequest;
import com.itsm.backend.ticket.service.TicketService;
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

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(CurrentUser user, @Valid @RequestBody CreateTicketRequest request) {
        Ticket ticket = ticketService.create(user, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(ticket.getId()).toUri();
        return ResponseEntity.created(location).body(TicketResponse.from(ticket, user));
    }

    @GetMapping
    public PageResponse<TicketResponse> list(
            CurrentUser user,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(
                ticketService.list(user, status, PageRequest.of(page, size, NEWEST_FIRST)),
                ticket -> TicketResponse.from(ticket, user));
    }

    @GetMapping("/{id}")
    public TicketResponse get(CurrentUser user, @PathVariable long id) {
        return TicketResponse.from(ticketService.get(user, id), user);
    }

    @PostMapping("/{id}/transitions")
    public TicketResponse transition(CurrentUser user, @PathVariable long id,
            @Valid @RequestBody TransitionRequest request) {
        return TicketResponse.from(ticketService.transition(user, id, request), user);
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(CurrentUser user, @PathVariable long id) {
        return ticketService.history(user, id).stream().map(StatusHistoryResponse::from).toList();
    }
}
