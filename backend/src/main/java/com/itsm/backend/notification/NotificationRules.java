package com.itsm.backend.notification;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketComment;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.user.UserDirectory;

/**
 * Who is notified about what. Pure functions, unit tested; nobody is notified about their own action.
 * See docs/adr/0007-in-app-notifications.md.
 */
public final class NotificationRules {

    /** A notification to be created. */
    public record Recipient(String userId, NotificationType type) {
    }

    private NotificationRules() {
    }

    /**
     * Status changes go to the requester and the assignee, except the actor. A ticket the system closed
     * automatically is reported to the requester only.
     */
    public static List<Recipient> forStatusChange(Ticket ticket, TicketStatus to, String actorId) {
        if (UserDirectory.SYSTEM_USER_ID.equals(actorId) && to == TicketStatus.CLOSED) {
            return List.of(new Recipient(ticket.getRequesterId(), NotificationType.AUTO_CLOSED));
        }
        return recipients(NotificationType.STATUS_CHANGED, actorId, ticket.getRequesterId(), ticket.getAssigneeId());
    }

    /**
     * Customer-visible comments go to the requester and the assignee; internal comments only to the
     * assignee, so a customer can never learn about them. The author is never notified.
     */
    public static List<Recipient> forComment(Ticket ticket, TicketComment comment) {
        if (comment.getVisibility() == CommentVisibility.INTERNAL) {
            return recipients(NotificationType.INTERNAL_COMMENT_ADDED, comment.getAuthorId(), ticket.getAssigneeId());
        }
        return recipients(NotificationType.COMMENT_ADDED, comment.getAuthorId(),
                ticket.getRequesterId(), ticket.getAssigneeId());
    }

    /** SLA breaches go to the assignee (if any) and every manager. */
    public static List<Recipient> forSlaBreach(Ticket ticket, Collection<String> managerIds) {
        Set<String> users = new LinkedHashSet<>();
        if (ticket.getAssigneeId() != null) {
            users.add(ticket.getAssigneeId());
        }
        users.addAll(managerIds);
        return users.stream().map(id -> new Recipient(id, NotificationType.SLA_BREACHED)).toList();
    }

    private static List<Recipient> recipients(NotificationType type, String actorId, String... candidates) {
        Set<String> users = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && !Objects.equals(candidate, actorId)) {
                users.add(candidate);
            }
        }
        return users.stream().map(id -> new Recipient(id, type)).toList();
    }
}
