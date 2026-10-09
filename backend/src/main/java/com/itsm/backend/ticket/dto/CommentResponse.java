package com.itsm.backend.ticket.dto;

import java.time.Instant;

import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.TicketComment;

public record CommentResponse(
        Long id,
        String authorId,
        CommentVisibility visibility,
        String body,
        Instant createdAt) {

    public static CommentResponse from(TicketComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getAuthorId(),
                comment.getVisibility(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}
