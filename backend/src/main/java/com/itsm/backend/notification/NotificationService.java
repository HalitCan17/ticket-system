package com.itsm.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.itsm.backend.notification.NotificationRules.Recipient;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.event.TicketCommentAddedEvent;
import com.itsm.backend.ticket.event.TicketStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Turns ticket events into notifications. Listeners run synchronously in the publisher's transaction,
 * so a notification exists exactly when the change it describes was committed.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @EventListener
    public void onStatusChanged(TicketStatusChangedEvent event) {
        create(event.ticket(), NotificationRules.forStatusChange(event.ticket(), event.to(), event.actorId()),
                event.actorId(), event.to());
    }

    @EventListener
    public void onCommentAdded(TicketCommentAddedEvent event) {
        create(event.ticket(), NotificationRules.forComment(event.ticket(), event.comment()),
                event.comment().getAuthorId(), null);
    }

    void create(Ticket ticket, List<Recipient> recipients, String actorId, TicketStatus status) {
        Instant now = clock.instant();
        notifications.saveAll(recipients.stream()
                .map(r -> new Notification(r.userId(), r.type(), ticket.getId(), actorId, status, now))
                .toList());
    }
}
