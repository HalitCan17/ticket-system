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
    TICKET_RATED
}
