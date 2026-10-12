package com.itsm.backend.cannedresponse;

import java.time.Instant;

import com.itsm.backend.ticket.domain.CommentVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A team-wide reply template; see docs/adr/0012-canned-responses.md. */
@Entity
@Table(name = "canned_responses")
public class CannedResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** Suggested visibility of a comment written from this template. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommentVisibility visibility;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CannedResponse() {
    }

    public CannedResponse(String title, String body, CommentVisibility visibility, String actorId, Instant now) {
        this.title = title;
        this.body = body;
        this.visibility = visibility;
        this.createdBy = actorId;
        this.createdAt = now;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void update(String title, String body, CommentVisibility visibility, String actorId, Instant now) {
        this.title = title;
        this.body = body;
        this.visibility = visibility;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
