package com.itsm.backend.kb;

import java.time.Instant;

import com.itsm.backend.ticket.domain.TicketCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A knowledge base article; see docs/adr/0016-knowledge-base.md. */
@Entity
@Table(name = "kb_articles")
public class KbArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KbVisibility visibility;

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

    protected KbArticle() {
    }

    public KbArticle(String title, String body, TicketCategory category, KbVisibility visibility, String actorId,
            Instant now) {
        this.title = title;
        this.body = body;
        this.category = category;
        this.visibility = visibility;
        this.createdBy = actorId;
        this.createdAt = now;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public void update(String title, String body, TicketCategory category, KbVisibility visibility, String actorId,
            Instant now) {
        this.title = title;
        this.body = body;
        this.category = category;
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

    public TicketCategory getCategory() {
        return category;
    }

    public KbVisibility getVisibility() {
        return visibility;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
