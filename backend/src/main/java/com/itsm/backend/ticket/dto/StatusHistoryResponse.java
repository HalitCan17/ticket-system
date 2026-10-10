package com.itsm.backend.ticket.dto;

import java.time.Instant;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.domain.TicketStatusHistory;

public record StatusHistoryResponse(
        TicketStatus fromStatus,
        TicketStatus toStatus,
        String changedBy,
        String changedByName,
        String note,
        Instant changedAt) {

    /** @param names display names by user id (see {@link com.itsm.backend.user.UserDirectory}) */
    public static StatusHistoryResponse from(TicketStatusHistory entry, Map<String, String> names) {
        return new StatusHistoryResponse(
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedBy(),
                names.get(entry.getChangedBy()),
                entry.getNote(),
                entry.getChangedAt());
    }
}
