package com.itsm.backend.ticket.domain;

/** Staff changes recorded in {@link TicketActivity}; status changes stay in {@link TicketStatusHistory}. */
public enum TicketActivityType {
    /** Values are user ids; null means unassigned. */
    ASSIGNEE_CHANGED,
    /** Values are {@link TicketPriority} names; the note is the required reason. */
    PRIORITY_CHANGED,
    /** Values are {@link TicketCategory} names. */
    CATEGORY_CHANGED,
    /** Values are support group ids; null means no group. */
    GROUP_CHANGED
}
