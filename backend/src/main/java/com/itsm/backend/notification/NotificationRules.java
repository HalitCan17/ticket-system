package com.itsm.backend.notification;

import java.util.ArrayList;
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

    /**
     * Same audience as comments: customer-visible attachments go to requester and assignee, internal ones
     * only to the assignee; never to the uploader.
     */
    public static List<Recipient> forAttachment(Ticket ticket, CommentVisibility visibility, String uploaderId) {
        return visibility == CommentVisibility.INTERNAL
                ? recipients(NotificationType.ATTACHMENT_ADDED, uploaderId, ticket.getAssigneeId())
                : recipients(NotificationType.ATTACHMENT_ADDED, uploaderId,
                        ticket.getRequesterId(), ticket.getAssigneeId());
    }

    /** A rating goes to the assignee who resolved the ticket (never to the requester who rated). */
    public static List<Recipient> forRating(Ticket ticket, String actorId) {
        return recipients(NotificationType.TICKET_RATED, actorId, ticket.getAssigneeId());
    }

    /**
     * The new assignee is told the ticket is theirs; the previous assignee that it no longer is.
     * Nobody is notified about their own action (e.g. claiming a ticket).
     */
    public static List<Recipient> forAssigneeChange(Ticket ticket, String previousAssigneeId, String actorId) {
        List<Recipient> result = new ArrayList<>(
                recipients(NotificationType.TICKET_ASSIGNED, actorId, ticket.getAssigneeId()));
        if (!Objects.equals(previousAssigneeId, ticket.getAssigneeId())) {
            result.addAll(recipients(NotificationType.TICKET_UNASSIGNED, actorId, previousAssigneeId));
        }
        return result;
    }

    /** A priority change goes to the assignee (the SLA they work against changed). */
    public static List<Recipient> forPriorityChange(Ticket ticket, String actorId) {
        return recipients(NotificationType.PRIORITY_CHANGED, actorId, ticket.getAssigneeId());
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
