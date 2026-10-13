package com.itsm.backend.notification;

import java.time.Instant;

import com.itsm.backend.ticket.domain.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    /** Exactly one of ticketId and serviceRequestId is set. */
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "service_request_id")
    private Long serviceRequestId;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status", length = 32)
    private TicketStatus ticketStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(String recipientId, NotificationType type, Long ticketId, String actorId,
            TicketStatus ticketStatus, Instant createdAt) {
        this.recipientId = recipientId;
        this.type = type;
        this.ticketId = ticketId;
        this.actorId = actorId;
        this.ticketStatus = ticketStatus;
        this.createdAt = createdAt;
    }

    /** A notification about a service request (approval asked, approved, rejected). */
    public static Notification aboutServiceRequest(String recipientId, NotificationType type, long serviceRequestId,
            String actorId, Instant createdAt) {
        Notification n = new Notification(recipientId, type, null, actorId, null, createdAt);
        n.serviceRequestId = serviceRequestId;
        return n;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Long getServiceRequestId() {
        return serviceRequestId;
    }

    public String getActorId() {
        return actorId;
    }

    public TicketStatus getTicketStatus() {
        return ticketStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
