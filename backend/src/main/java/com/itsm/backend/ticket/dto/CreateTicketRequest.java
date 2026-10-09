package com.itsm.backend.ticket.dto;

import com.itsm.backend.ticket.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10_000) String description,
        @NotNull TicketPriority priority) {
}
