package com.itsm.backend.ticket.domain;

import java.time.Duration;
import java.time.Instant;

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

    @Version
    @Column(nullable = false)
    private long version;

    protected Ticket() {
    }

    public static Ticket open(String title, String description, TicketPriority priority, String requesterId,
            Duration slaTarget, Instant now) {
        Ticket ticket = new Ticket();
        ticket.title = title;
        ticket.description = description;
        ticket.priority = priority;
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
