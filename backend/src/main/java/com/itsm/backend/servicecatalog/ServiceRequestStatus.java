package com.itsm.backend.servicecatalog;

/**
 * Where a service request is. An approved request does not stay "approved": it immediately becomes a ticket, so it
 * goes straight to {@link #FULFILLING}; from then on the ticket shows the progress.
 */
public enum ServiceRequestStatus {
    PENDING_APPROVAL, FULFILLING, REJECTED, CANCELLED
}
