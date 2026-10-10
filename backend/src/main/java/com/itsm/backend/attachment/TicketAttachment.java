package com.itsm.backend.attachment;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Metadata of a file attached to a ticket; the content is in {@link AttachmentStorage} under {@code storageKey}. */
@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "uploader_id", nullable = false, length = 64)
    private String uploaderId;

    /** Same meaning as for comments: INTERNAL is staff-only. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommentVisibility visibility;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 64, unique = true)
    private String storageKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketAttachment() {
    }

    public TicketAttachment(Long ticketId, String uploaderId, CommentVisibility visibility, String originalName,
            String contentType, AttachmentStorage.StoredFile stored, Instant createdAt) {
        this.ticketId = ticketId;
        this.uploaderId = uploaderId;
        this.visibility = visibility;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = stored.size();
        this.storageKey = stored.key();
        this.sha256 = stored.sha256();
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
