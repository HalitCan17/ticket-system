package com.itsm.backend.notification;

import java.time.Instant;

import com.itsm.backend.ticket.domain.TicketStatus;

/**
 * The client builds the sentence from {@code type} (and {@code ticketStatus} for status changes),
 * so wording and language stay in the UI.
 *
 * @param actorName display name of who caused it; "System" for automatic changes, null if unknown
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        Long ticketId,
        String ticketTitle,
        TicketStatus ticketStatus,
        String actorId,
        String actorName,
        Instant createdAt,
        boolean read) {
}
