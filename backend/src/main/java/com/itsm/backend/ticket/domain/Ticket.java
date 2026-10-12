package com.itsm.backend.ticket.domain;

import java.time.Duration;
import java.time.Instant;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.security.CurrentUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketCategory category;

    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;

    @Column(name = "assignee_id", length = 64)
    private String assigneeId;

    /** Support group handling the ticket; set from the category's route (docs/adr/0014-support-groups.md). */
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "sla_target_seconds", nullable = false)
    private long slaTargetSeconds;

    @Column(name = "sla_elapsed_seconds", nullable = false)
    private long slaElapsedSeconds;

    @Column(name = "sla_running_since")
    private Instant slaRunningSince;

    @Column(name = "sla_breach_notified_at")
    private Instant slaBreachNotifiedAt;

    /** Derived from the SLA clock (see {@link SlaClock#scheduledDueAt}); only for ordering the work queue. */
    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    /** Requester's satisfaction score 1–5; null until rated. */
    @Column
    private Short rating;

    @Column(name = "rating_comment", columnDefinition = "text")
    private String ratingComment;

    @Column(name = "rated_at")
    private Instant ratedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Ticket() {
    }

    public static Ticket open(String title, String description, TicketPriority priority, TicketCategory category,
            String requesterId, Duration slaTarget, Instant now) {
        Ticket ticket = new Ticket();
        ticket.title = title;
        ticket.description = description;
        ticket.priority = priority;
        ticket.category = category;
        ticket.requesterId = requesterId;
        ticket.status = TicketStatus.NEW;
        ticket.createdAt = now;
        ticket.updatedAt = now;
        ticket.setSlaClock(SlaClock.start(slaTarget, now));
        return ticket;
    }

    /**
     * Validates the transition with {@link TicketWorkflow} and applies its side effects.
     *
     * @return the status before the transition
     */
    public TicketStatus transitionTo(TicketStatus target, CurrentUser actor, String note, Instant now) {
        TicketWorkflow.validate(status, target, actor, requesterId, note);
        TicketStatus previous = status;

        if (previous == TicketStatus.NEW && target == TicketStatus.IN_PROGRESS && assigneeId == null) {
            assigneeId = actor.id();
        }
        if (target == TicketStatus.RESOLVED) {
            resolutionNote = note.strip();
            resolvedAt = now;
        }
        if (previous == TicketStatus.RESOLVED && target == TicketStatus.IN_PROGRESS) {
            // Objection: the previous resolution stays in the status history.
            resolutionNote = null;
            resolvedAt = null;
        }
        if (target == TicketStatus.CLOSED) {
            closedAt = now;
        }

        status = target;
        updatedAt = now;
        updateSlaClock(now);
        return previous;
    }

    /**
     * Closes a RESOLVED ticket on behalf of the system (no customer response in time).
     * Bypasses actor rules; see {@link TicketWorkflow#validateSystemClose}.
     */
    public void autoClose(Instant now) {
        TicketWorkflow.validateSystemClose(status);
        status = TicketStatus.CLOSED;
        closedAt = now;
        updatedAt = now;
        updateSlaClock(now);
    }

    /**
     * Changes the assignee following {@link TicketAssignment}.
     *
     * @return the previous assignee id (null if there was none)
     * @throws ConflictException if the ticket is closed ({@code TICKET_CLOSED})
     */
    public String assignTo(String newAssigneeId, boolean newAssigneeIsStaff, CurrentUser actor, Instant now) {
        if (isClosed()) {
            throw new ConflictException("TICKET_CLOSED", "The assignee of a closed ticket cannot change.");
        }
        TicketAssignment.validate(actor, assigneeId, newAssigneeId, newAssigneeIsStaff);
        String previous = assigneeId;
        assigneeId = newAssigneeId;
        updatedAt = now;
        return previous;
    }

    /**
     * Moves the ticket to another support group (or none). The assignee stays.
     *
     * @return the previous group id
     */
    public Long moveToGroup(Long newGroupId, Instant now) {
        Long previous = groupId;
        groupId = newGroupId;
        updatedAt = now;
        return previous;
    }

    /**
     * Assignment made by the application itself (group routing). Only an open, unassigned ticket is assigned;
     * the actor rules of {@link TicketAssignment} do not apply to the system.
     *
     * @return whether the ticket was assigned
     */
    public boolean assignAutomatically(String userId, Instant now) {
        if (assigneeId != null || isClosed()) {
            return false;
        }
        assigneeId = userId;
        updatedAt = now;
        return true;
    }

    /** Staff may change priority and category while the ticket is not closed. */
    public boolean canBeEditedBy(CurrentUser actor) {
        return actor.isStaff() && !isClosed();
    }

    /**
     * Changes the priority and measures the SLA against the new priority's target; the time used so far is kept.
     * If the ticket is no longer breached, a later breach is notified again.
     *
     * @return the previous priority
     * @throws ForbiddenException  for customers ({@code TICKET_EDIT_NOT_ALLOWED})
     * @throws ConflictException   when closed ({@code TICKET_CLOSED}) or resolved ({@code PRIORITY_LOCKED}: SLA stopped)
     * @throws BadRequestException without a reason ({@code PRIORITY_REASON_REQUIRED})
     */
    public TicketPriority changePriority(TicketPriority newPriority, Duration newSlaTarget, String reason,
            CurrentUser actor, Instant now) {
        checkEditable(actor);
        if (status == TicketStatus.RESOLVED) {
            throw new ConflictException("PRIORITY_LOCKED", "The priority of a resolved ticket cannot change.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("PRIORITY_REASON_REQUIRED", "A reason is required to change the priority.");
        }
        TicketPriority previous = priority;
        priority = newPriority;
        SlaClock clock = getSlaClock().withTarget(newSlaTarget);
        setSlaClock(clock);
        if (!clock.isBreached(now)) {
            slaBreachNotifiedAt = null;
        }
        updatedAt = now;
        return previous;
    }

    /**
     * @return the previous category
     * @throws ForbiddenException for customers; {@link ConflictException} when closed
     */
    public TicketCategory changeCategory(TicketCategory newCategory, CurrentUser actor, Instant now) {
        checkEditable(actor);
        TicketCategory previous = category;
        category = newCategory;
        updatedAt = now;
        return previous;
    }

    private void checkEditable(CurrentUser actor) {
        if (!actor.isStaff()) {
            throw new ForbiddenException("TICKET_EDIT_NOT_ALLOWED", "Only staff can change a ticket's details.");
        }
        if (isClosed()) {
            throw new ConflictException("TICKET_CLOSED", "A closed ticket cannot be changed.");
        }
    }

    /**
     * Whether {@code user} may rate this ticket now: only the requester, only once, and only a ticket that
     * was actually resolved (RESOLVED, or CLOSED after a resolution — a withdrawn ticket has no resolution).
     */
    public boolean canBeRatedBy(CurrentUser user) {
        return user.id().equals(requesterId) && rating == null && isResolved();
    }

    /**
     * Records the requester's satisfaction score.
     *
     * @throws ForbiddenException if {@code user} is not the requester ({@code RATING_NOT_ALLOWED})
     * @throws ConflictException  if already rated ({@code ALREADY_RATED}) or not resolved ({@code TICKET_NOT_RESOLVED})
     */
    public void rate(CurrentUser user, int score, String comment, Instant now) {
        if (!user.id().equals(requesterId)) {
            throw new ForbiddenException("RATING_NOT_ALLOWED", "Only the requester can rate this ticket.");
        }
        if (rating != null) {
            throw new ConflictException("ALREADY_RATED", "This ticket has already been rated.");
        }
        if (!isResolved()) {
            throw new ConflictException("TICKET_NOT_RESOLVED", "Only resolved tickets can be rated.");
        }
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("score must be between 1 and 5: " + score);
        }
        rating = (short) score;
        ratingComment = comment == null || comment.isBlank() ? null : comment.strip();
        ratedAt = now;
    }

    private boolean isResolved() {
        return (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) && resolutionNote != null;
    }

    /** An SLA breach is notified once per ticket, also if an objection later resumes the clock. */
    public void markSlaBreachNotified(Instant now) {
        slaBreachNotifiedAt = now;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public Instant getSlaBreachNotifiedAt() {
        return slaBreachNotifiedAt;
    }

    public SlaClock getSlaClock() {
        return new SlaClock(Duration.ofSeconds(slaTargetSeconds), Duration.ofSeconds(slaElapsedSeconds), slaRunningSince);
    }

    public SlaState getSlaState() {
        return switch (status) {
            case NEW, IN_PROGRESS -> SlaState.RUNNING;
            case WAITING_FOR_CUSTOMER -> SlaState.PAUSED;
            case RESOLVED, CLOSED -> SlaState.STOPPED;
        };
    }

    /** The clock runs in NEW/IN_PROGRESS and is halted in every other status. */
    private void updateSlaClock(Instant now) {
        SlaClock clock = getSlaClock();
        setSlaClock(getSlaState() == SlaState.RUNNING ? clock.resume(now) : clock.halt(now));
    }

    private void setSlaClock(SlaClock clock) {
        slaTargetSeconds = clock.target().toSeconds();
        slaElapsedSeconds = clock.elapsedBeforeRun().toSeconds();
        slaRunningSince = clock.runningSince();
        slaDueAt = clock.scheduledDueAt();
    }

    public boolean isClosed() {
        return status == TicketStatus.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public Integer getRating() {
        return rating == null ? null : rating.intValue();
    }

    public String getRatingComment() {
        return ratingComment;
    }

    public Instant getRatedAt() {
        return ratedAt;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public long getVersion() {
        return version;
    }
}
