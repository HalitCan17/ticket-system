package com.itsm.backend.servicecatalog;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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

/** A request for a catalog item. The jBPM process moves it; this row is what everybody reads. */
@Entity
@Table(name = "service_requests")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> answers = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceRequestStatus status;

    @Column(name = "process_instance_id", length = 36)
    private String processInstanceId;

    @Column(name = "approver_id", length = 64)
    private String approverId;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ServiceRequest() {
    }

    /** Requests needing approval wait for it; the others are fulfilled right away by the process. */
    public ServiceRequest(long itemId, String requesterId, Map<String, String> answers, boolean requiresApproval,
            Instant now) {
        this.itemId = itemId;
        this.requesterId = requesterId;
        this.answers = new LinkedHashMap<>(answers);
        this.status = requiresApproval ? ServiceRequestStatus.PENDING_APPROVAL : ServiceRequestStatus.FULFILLING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return status == ServiceRequestStatus.PENDING_APPROVAL;
    }

    public void attachProcess(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    /** Recorded before the approval task is completed; the process then fulfils or rejects. */
    public void recordDecision(String approverId, String comment, Instant now) {
        this.approverId = approverId;
        this.decisionComment = comment;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    public void fulfilledBy(long ticketId, Instant now) {
        this.ticketId = ticketId;
        this.status = ServiceRequestStatus.FULFILLING;
        this.updatedAt = now;
    }

    public void reject(Instant now) {
        this.status = ServiceRequestStatus.REJECTED;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = ServiceRequestStatus.CANCELLED;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public Map<String, String> getAnswers() {
        return Map.copyOf(answers);
    }

    /** Answers in the order they were given (the form's order). */
    public Map<String, String> getOrderedAnswers() {
        return new LinkedHashMap<>(answers);
    }

    public ServiceRequestStatus getStatus() {
        return status;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public String getApproverId() {
        return approverId;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
