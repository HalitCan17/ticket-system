package com.itsm.backend.ticket.dto;

import java.time.Instant;
import java.util.Map;

import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.TicketComment;

public record CommentResponse(
        Long id,
        String authorId,
        String authorName,
        CommentVisibility visibility,
        String body,
        Instant createdAt) {

    /** @param names display names by user id (see {@link com.itsm.backend.user.UserDirectory}) */
    public static CommentResponse from(TicketComment comment, Map<String, String> names) {
        return new CommentResponse(
                comment.getId(),
                comment.getAuthorId(),
                names.get(comment.getAuthorId()),
                comment.getVisibility(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}
