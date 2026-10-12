package com.itsm.backend.cannedresponse;

import com.itsm.backend.ticket.domain.CommentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CannedResponseRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 5000) String body,
        @NotNull CommentVisibility visibility) {
}
