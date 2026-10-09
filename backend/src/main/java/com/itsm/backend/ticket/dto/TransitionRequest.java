package com.itsm.backend.ticket.dto;

import com.itsm.backend.ticket.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param note resolution note (→ RESOLVED) or objection reason (RESOLVED → IN_PROGRESS); optional otherwise
 */
public record TransitionRequest(
        @NotNull TicketStatus targetStatus,
        @Size(max = 5_000) String note) {
}
