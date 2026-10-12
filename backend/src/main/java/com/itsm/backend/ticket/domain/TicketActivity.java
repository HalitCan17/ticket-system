package com.itsm.backend.ticket.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One staff change to a ticket (assignee, priority, category). Visible to staff only. */
@Entity
@Table(name = "ticket_activities")
public class TicketActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketActivityType type;

    @Column(name = "old_value", length = 64)
    private String oldValue;

    @Column(name = "new_value", length = 64)
    private String newValue;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketActivity() {
    }

    public TicketActivity(Long ticketId, TicketActivityType type, String oldValue, String newValue, String note,
            String actorId, Instant createdAt) {
        this.ticketId = ticketId;
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.note = note;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public TicketActivityType getType() {
        return type;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getNote() {
        return note;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
