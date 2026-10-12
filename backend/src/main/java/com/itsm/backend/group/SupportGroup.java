package com.itsm.backend.group;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A team that handles some categories; see docs/adr/0014-support-groups.md. */
@Entity
@Table(name = "support_groups")
public class SupportGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_mode", nullable = false, length = 16)
    private AssignmentMode assignmentMode;

    @Column(name = "last_assigned_id", length = 64)
    private String lastAssignedId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "support_group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id", length = 64)
    private Set<String> memberIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SupportGroup() {
    }

    public SupportGroup(String name, String description, AssignmentMode assignmentMode, Instant now) {
        this.name = name;
        this.description = description;
        this.assignmentMode = assignmentMode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, AssignmentMode assignmentMode, Instant now) {
        this.name = name;
        this.description = description;
        this.assignmentMode = assignmentMode;
        this.updatedAt = now;
    }

    public void replaceMembers(Set<String> userIds, Instant now) {
        memberIds.clear();
        memberIds.addAll(userIds);
        updatedAt = now;
    }

    /** Moves the round-robin cursor; called while the group row is locked. */
    public void recordAutomaticAssignment(String userId) {
        lastAssignedId = userId;
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

    public AssignmentMode getAssignmentMode() {
        return assignmentMode;
    }

    public String getLastAssignedId() {
        return lastAssignedId;
    }

    public Set<String> getMemberIds() {
        return Set.copyOf(memberIds);
    }
}
