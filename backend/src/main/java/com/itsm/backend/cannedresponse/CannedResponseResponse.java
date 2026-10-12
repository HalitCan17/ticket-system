package com.itsm.backend.cannedresponse;

import java.time.Instant;

import com.itsm.backend.ticket.domain.CommentVisibility;

public record CannedResponseResponse(
        Long id, String title, String body, CommentVisibility visibility, String updatedBy, Instant updatedAt) {

    static CannedResponseResponse from(CannedResponse c) {
        return new CannedResponseResponse(c.getId(), c.getTitle(), c.getBody(), c.getVisibility(), c.getUpdatedBy(),
                c.getUpdatedAt());
    }
}
