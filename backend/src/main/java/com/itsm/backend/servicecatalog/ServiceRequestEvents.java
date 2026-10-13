package com.itsm.backend.servicecatalog;

/** Published inside the transaction that changed the request; turned into notifications. */
public final class ServiceRequestEvents {

    private ServiceRequestEvents() {
    }

    /** A request waits for a manager's decision. */
    public record ApprovalRequested(long requestId, String requesterId) {
    }

    /** A manager decided; an approved request already has its ticket. */
    public record Decided(long requestId, String requesterId, boolean approved, String approverId) {
    }
}
