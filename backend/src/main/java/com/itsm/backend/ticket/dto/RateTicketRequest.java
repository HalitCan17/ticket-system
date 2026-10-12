package com.itsm.backend.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The requester's satisfaction score for a resolved ticket. */
public record RateTicketRequest(
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max = 1000) String comment) {
}
