package com.itsm.backend.notification;

import static com.itsm.backend.notification.NotificationType.ATTACHMENT_ADDED;
import static com.itsm.backend.notification.NotificationType.AUTO_CLOSED;
import static com.itsm.backend.notification.NotificationType.COMMENT_ADDED;
import static com.itsm.backend.notification.NotificationType.INTERNAL_COMMENT_ADDED;
import static com.itsm.backend.notification.NotificationType.SLA_BREACHED;
import static com.itsm.backend.notification.NotificationType.STATUS_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.itsm.backend.notification.NotificationRules.Recipient;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.CommentVisibility;
import com.itsm.backend.ticket.domain.Ticket;
import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketComment;
import com.itsm.backend.ticket.domain.TicketPriority;
import com.itsm.backend.ticket.domain.TicketStatus;
import com.itsm.backend.user.UserDirectory;
import org.junit.jupiter.api.Test;

class NotificationRulesTest {

    static final Instant T0 = Instant.parse("2026-10-11T09:00:00Z");
    static final String CUSTOMER = "cust1";
    static final String AGENT = "agent1";
    static final String MANAGER = "manager1";

    @Test
    void agentStartingWorkNotifiesOnlyTheRequester() {
        Ticket ticket = newTicket();
        ticket.transitionTo(TicketStatus.IN_PROGRESS, agent(), null, T0); // assigns agent1

        assertThat(NotificationRules.forStatusChange(ticket, TicketStatus.IN_PROGRESS, AGENT))
                .containsExactly(new Recipient(CUSTOMER, STATUS_CHANGED));
    }

    @Test
    void customerObjectionNotifiesTheAssignee() {
        Ticket ticket = assignedTo(AGENT);

        assertThat(NotificationRules.forStatusChange(ticket, TicketStatus.IN_PROGRESS, CUSTOMER))
                .containsExactly(new Recipient(AGENT, STATUS_CHANGED));
    }

    @Test
    void managerChangeNotifiesRequesterAndAssignee() {
        Ticket ticket = assignedTo(AGENT);

        assertThat(NotificationRules.forStatusChange(ticket, TicketStatus.CLOSED, MANAGER))
                .containsExactly(new Recipient(CUSTOMER, STATUS_CHANGED), new Recipient(AGENT, STATUS_CHANGED));
    }

    @Test
    void assigneeChangeNotifiesNewAndPreviousAssigneeButNotTheActor() {
        Ticket ticket = assignedTo("agent2");

        // Manager moved it from agent1 to agent2
        assertThat(NotificationRules.forAssigneeChange(ticket, AGENT, MANAGER)).containsExactly(
                new Recipient("agent2", NotificationType.TICKET_ASSIGNED),
                new Recipient(AGENT, NotificationType.TICKET_UNASSIGNED));
        // agent1 handed it over themselves: only agent2 hears about it
        assertThat(NotificationRules.forAssigneeChange(ticket, AGENT, AGENT))
                .containsExactly(new Recipient("agent2", NotificationType.TICKET_ASSIGNED));
        // agent2 claimed an unassigned ticket: nobody
        assertThat(NotificationRules.forAssigneeChange(ticket, null, "agent2")).isEmpty();
    }

    @Test
    void unassigningNotifiesThePreviousAssignee() {
        assertThat(NotificationRules.forAssigneeChange(newTicket(), AGENT, MANAGER))
                .containsExactly(new Recipient(AGENT, NotificationType.TICKET_UNASSIGNED));
    }

    @Test
    void priorityChangeNotifiesTheAssignee() {
        assertThat(NotificationRules.forPriorityChange(assignedTo(AGENT), MANAGER))
                .containsExactly(new Recipient(AGENT, NotificationType.PRIORITY_CHANGED));
        assertThat(NotificationRules.forPriorityChange(assignedTo(AGENT), AGENT)).isEmpty();
    }

    @Test
    void ratingNotifiesOnlyTheAssignee() {
        assertThat(NotificationRules.forRating(assignedTo(AGENT), CUSTOMER))
                .containsExactly(new Recipient(AGENT, NotificationType.TICKET_RATED));
        assertThat(NotificationRules.forRating(newTicket(), CUSTOMER)).isEmpty();
    }

    @Test
    void autoCloseNotifiesOnlyTheRequester() {
        Ticket ticket = assignedTo(AGENT);

        assertThat(NotificationRules.forStatusChange(ticket, TicketStatus.CLOSED, UserDirectory.SYSTEM_USER_ID))
                .containsExactly(new Recipient(CUSTOMER, AUTO_CLOSED));
    }

    @Test
    void externalCommentsNotifyTheOtherSide() {
        Ticket ticket = assignedTo(AGENT);

        assertThat(NotificationRules.forComment(ticket, comment(AGENT, CommentVisibility.EXTERNAL)))
                .containsExactly(new Recipient(CUSTOMER, COMMENT_ADDED));
        assertThat(NotificationRules.forComment(ticket, comment(CUSTOMER, CommentVisibility.EXTERNAL)))
                .containsExactly(new Recipient(AGENT, COMMENT_ADDED));
    }

    @Test
    void internalCommentsNeverReachTheCustomer() {
        Ticket ticket = assignedTo(AGENT);

        assertThat(NotificationRules.forComment(ticket, comment(MANAGER, CommentVisibility.INTERNAL)))
                .containsExactly(new Recipient(AGENT, INTERNAL_COMMENT_ADDED));
        assertThat(NotificationRules.forComment(ticket, comment(AGENT, CommentVisibility.INTERNAL))).isEmpty();
    }

    @Test
    void unassignedTicketCommentNotifiesNobodyButTheRequester() {
        Ticket ticket = newTicket();

        assertThat(NotificationRules.forComment(ticket, comment(CUSTOMER, CommentVisibility.EXTERNAL))).isEmpty();
        assertThat(NotificationRules.forComment(ticket, comment(MANAGER, CommentVisibility.EXTERNAL)))
                .containsExactly(new Recipient(CUSTOMER, COMMENT_ADDED));
    }

    @Test
    void attachmentsFollowTheCommentAudience() {
        Ticket ticket = assignedTo(AGENT);

        assertThat(NotificationRules.forAttachment(ticket, CommentVisibility.EXTERNAL, CUSTOMER))
                .containsExactly(new Recipient(AGENT, ATTACHMENT_ADDED));
        assertThat(NotificationRules.forAttachment(ticket, CommentVisibility.EXTERNAL, AGENT))
                .containsExactly(new Recipient(CUSTOMER, ATTACHMENT_ADDED));
        assertThat(NotificationRules.forAttachment(ticket, CommentVisibility.INTERNAL, MANAGER))
                .containsExactly(new Recipient(AGENT, ATTACHMENT_ADDED));
    }

    @Test
    void slaBreachEscalatesToAssigneeGroupAndManagersOnce() {
        assertThat(NotificationRules.forSlaBreach(assignedTo(AGENT), List.of("agent2", AGENT),
                List.of(MANAGER, "manager2", AGENT)))
                .containsExactly(
                        new Recipient(AGENT, SLA_BREACHED),
                        new Recipient("agent2", SLA_BREACHED),
                        new Recipient(MANAGER, SLA_BREACHED),
                        new Recipient("manager2", SLA_BREACHED));
        assertThat(NotificationRules.forSlaBreach(newTicket(), List.of(), List.of(MANAGER)))
                .containsExactly(new Recipient(MANAGER, SLA_BREACHED));
    }

    @Test
    void warningsAndLateFirstResponsesGoToWhoeverOwnsTheTicket() {
        // The assignee if there is one ...
        assertThat(NotificationRules.forSlaWarning(assignedTo(AGENT), List.of("agent2"), List.of(MANAGER)))
                .containsExactly(new Recipient(AGENT, NotificationType.SLA_WARNING));
        // ... otherwise the group's members ...
        assertThat(NotificationRules.forFirstResponseBreach(newTicket(), List.of("agent2", "agent3"), List.of(MANAGER)))
                .containsExactly(new Recipient("agent2", NotificationType.FIRST_RESPONSE_BREACHED),
                        new Recipient("agent3", NotificationType.FIRST_RESPONSE_BREACHED));
        // ... otherwise the managers.
        assertThat(NotificationRules.forSlaWarning(newTicket(), List.of(), List.of(MANAGER)))
                .containsExactly(new Recipient(MANAGER, NotificationType.SLA_WARNING));
    }

    private static Ticket newTicket() {
        return Ticket.open("VPN", "down", TicketPriority.HIGH, TicketCategory.OTHER, CUSTOMER, Duration.ofHours(8), T0);
    }

    private static Ticket assignedTo(String agentId) {
        Ticket ticket = newTicket();
        ticket.transitionTo(TicketStatus.IN_PROGRESS, new CurrentUser(agentId, Role.AGENT), null, T0);
        return ticket;
    }

    private static CurrentUser agent() {
        return new CurrentUser(AGENT, Role.AGENT);
    }

    private static TicketComment comment(String author, CommentVisibility visibility) {
        return new TicketComment(1L, author, visibility, "text", T0);
    }
}
