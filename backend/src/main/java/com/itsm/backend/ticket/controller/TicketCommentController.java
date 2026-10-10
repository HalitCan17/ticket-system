package com.itsm.backend.ticket.controller;

import java.util.List;
import java.util.Map;

import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.TicketComment;
import com.itsm.backend.ticket.dto.CommentResponse;
import com.itsm.backend.ticket.dto.CreateCommentRequest;
import com.itsm.backend.ticket.service.TicketCommentService;
import com.itsm.backend.user.UserDirectory;
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
    private final UserDirectory userDirectory;

    public TicketCommentController(TicketCommentService commentService, UserDirectory userDirectory) {
        this.commentService = commentService;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    public List<CommentResponse> list(CurrentUser user, @PathVariable long ticketId) {
        List<TicketComment> comments = commentService.list(user, ticketId);
        Map<String, String> names = userDirectory.displayNames(
                comments.stream().map(TicketComment::getAuthorId).toList());
        return comments.stream().map(comment -> CommentResponse.from(comment, names)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(CurrentUser user, @PathVariable long ticketId,
            @Valid @RequestBody CreateCommentRequest request) {
        TicketComment comment = commentService.add(user, ticketId, request);
        return CommentResponse.from(comment, userDirectory.displayNames(List.of(comment.getAuthorId())));
    }
}
