package com.itsm.backend.servicecatalog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.ticket.domain.TicketPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Something that can be requested from the service desk; see docs/adr/0017-service-catalog.md. */
@Entity
@Table(name = "catalog_items")
public class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /** The ticket opened for a request gets this category, so it is routed to the category's group. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketPriority priority;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<CatalogField> fields = new ArrayList<>();

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

    protected CatalogItem() {
    }

    public CatalogItem(String actorId, Instant now) {
        this.createdBy = actorId;
        this.createdAt = now;
    }

    public void update(String name, String description, TicketCategory category, TicketPriority priority,
            boolean requiresApproval, boolean active, List<CatalogField> fields, String actorId, Instant now) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.requiresApproval = requiresApproval;
        this.active = active;
        this.fields = new ArrayList<>(fields);
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public boolean isActive() {
        return active;
    }

    public List<CatalogField> getFields() {
        return List.copyOf(fields);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
