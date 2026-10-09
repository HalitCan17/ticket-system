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

@Entity
@Table(name = "ticket_comments")
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommentVisibility visibility;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketComment() {
    }

    public TicketComment(Long ticketId, String authorId, CommentVisibility visibility, String body, Instant createdAt) {
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.visibility = visibility;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
