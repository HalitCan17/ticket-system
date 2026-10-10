package com.itsm.backend.ticket.domain;

public enum SlaState {
    /** Clock is running (NEW, IN_PROGRESS). */
    RUNNING,
    /** Clock is paused (WAITING_FOR_CUSTOMER). */
    PAUSED,
    /** Clock stopped by a resolution (RESOLVED, CLOSED). */
    STOPPED
}
