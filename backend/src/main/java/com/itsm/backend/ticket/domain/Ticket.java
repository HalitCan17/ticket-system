package com.itsm.backend.ticket.domain;

import java.time.Duration;
import java.time.Instant;

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
