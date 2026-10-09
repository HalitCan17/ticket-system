package com.itsm.backend.ticket.dto;

import java.time.Instant;

import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;

public record StatusHistoryResponse(
        TicketStatus fromStatus,
        TicketStatus toStatus,
        String changedBy,
        String note,
        Instant changedAt) {

    public static StatusHistoryResponse from(TicketStatusHistory entry) {
        return new StatusHistoryResponse(
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedBy(),
                entry.getNote(),
                entry.getChangedAt());
    }
}
