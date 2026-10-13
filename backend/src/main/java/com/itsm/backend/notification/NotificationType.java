package com.itsm.backend.notification;

public enum NotificationType {
    /** A participant changed the ticket's status (see {@code ticketStatus}). */
    STATUS_CHANGED,
    /** The system closed a RESOLVED ticket the customer did not answer. */
    AUTO_CLOSED,
    /** A customer-visible comment was added. */
    COMMENT_ADDED,
    /** A staff-only comment was added. Never sent to customers. */
    INTERNAL_COMMENT_ADDED,
    /** The ticket exceeded its resolution SLA. */
    SLA_BREACHED,
    /** A file was attached; internal attachments are reported to staff only. */
    ATTACHMENT_ADDED,
    /** The requester rated the resolution (score in the ticket). */
    TICKET_RATED,
    /** The ticket was assigned to the recipient. */
    TICKET_ASSIGNED,
    /** The ticket was taken away from the recipient (reassigned or unassigned). */
    TICKET_UNASSIGNED,
    /** Staff changed the priority of the recipient's ticket. */
    PRIORITY_CHANGED,
    /** 80 % of the resolution SLA is used. */
    SLA_WARNING,
    /** Nobody from staff answered the customer within the first response target. */
    FIRST_RESPONSE_BREACHED,
    /** A service request waits for a manager's approval (see {@code serviceRequestId}). */
    APPROVAL_REQUESTED,
    /** The recipient's service request was approved and became a ticket. */
    REQUEST_APPROVED,
    /** The recipient's service request was rejected (reason on the request). */
    REQUEST_REJECTED
}
