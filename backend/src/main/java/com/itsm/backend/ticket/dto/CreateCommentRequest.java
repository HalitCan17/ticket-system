package com.itsm.backend.ticket.dto;

import com.itsm.backend.ticket.domain.CommentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank @Size(max = 10_000) String body,
        @NotNull CommentVisibility visibility) {
}
