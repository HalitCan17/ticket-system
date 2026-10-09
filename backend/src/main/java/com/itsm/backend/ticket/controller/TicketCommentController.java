package com.itsm.backend.ticket.controller;

import java.util.List;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.dto.CommentResponse;
import com.itsm.backend.ticket.dto.CreateCommentRequest;
import com.itsm.backend.ticket.service.TicketCommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class TicketCommentController {

    private final TicketCommentService commentService;

    public TicketCommentController(TicketCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<CommentResponse> list(CurrentUser user, @PathVariable long ticketId) {
        return commentService.list(user, ticketId).stream().map(CommentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(CurrentUser user, @PathVariable long ticketId,
            @Valid @RequestBody CreateCommentRequest request) {
        return CommentResponse.from(commentService.add(user, ticketId, request));
    }
}
