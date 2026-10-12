package com.itsm.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.itsm.backend.attachment.TicketAttachment;
import com.itsm.backend.attachment.TicketAttachmentAddedEvent;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.notification.NotificationRules.Recipient;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.ticket.event.TicketCommentAddedEvent;
import com.itsm.backend.ticket.event.TicketAssigneeChangedEvent;
import com.itsm.backend.ticket.event.TicketPriorityChangedEvent;
import com.itsm.backend.ticket.event.TicketRatedEvent;
import com.itsm.backend.ticket.event.TicketStatusChangedEvent;
import com.itsm.backend.user.UserDirectory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns ticket events into notifications. Listeners run synchronously in the publisher's transaction,
 * so a notification exists exactly when the change it describes was committed.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserDirectory userDirectory;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserDirectory userDirectory, Clock clock) {
        this.notifications = notifications;
        this.userDirectory = userDirectory;
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

    @EventListener
    public void onAttachmentAdded(TicketAttachmentAddedEvent event) {
        TicketAttachment attachment = event.attachment();
        create(event.ticket(), NotificationRules.forAttachment(event.ticket(), attachment.getVisibility(),
                attachment.getUploaderId()), attachment.getUploaderId(), null);
    }

    @EventListener
    public void onRated(TicketRatedEvent event) {
        create(event.ticket(), NotificationRules.forRating(event.ticket(), event.actorId()), event.actorId(), null);
    }

    @EventListener
    public void onAssigneeChanged(TicketAssigneeChangedEvent event) {
        create(event.ticket(), NotificationRules.forAssigneeChange(event.ticket(), event.previousAssigneeId(),
                event.actorId()), event.actorId(), null);
    }

    @EventListener
    public void onPriorityChanged(TicketPriorityChangedEvent event) {
        create(event.ticket(), NotificationRules.forPriorityChange(event.ticket(), event.actorId()), event.actorId(), null);
    }

    /** Notifies the assignee and all known managers that the ticket exceeded its SLA. */
    public void notifySlaBreach(Ticket ticket) {
        create(ticket, NotificationRules.forSlaBreach(ticket, userDirectory.managerIds()), null, null);
    }

    /** The caller's own notifications only. */
    @Transactional(readOnly = true)
    public Page<Notification> list(CurrentUser user, boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNull(user.id(), pageable)
                : notifications.findByRecipientId(user.id(), pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser user) {
        return notifications.countByRecipientIdAndReadAtIsNull(user.id());
    }

    /** Someone else's notification answers 404 like a missing one. */
    @Transactional
    public void markRead(CurrentUser user, long id) {
        notifications.findByIdAndRecipientId(id, user.id())
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification " + id + " not found."))
                .markRead(clock.instant());
    }

    @Transactional
    public int markAllRead(CurrentUser user) {
        return notifications.markAllRead(user.id(), clock.instant());
    }

    void create(Ticket ticket, List<Recipient> recipients, String actorId, TicketStatus status) {
        Instant now = clock.instant();
        notifications.saveAll(recipients.stream()
                .map(r -> new Notification(r.userId(), r.type(), ticket.getId(), actorId, status, now))
                .toList());
    }
}
