package com.itsm.backend.ticket.domain;

public enum CommentVisibility {
    /** Visible to AGENT and MANAGER only. */
    INTERNAL,
    /** Visible to the customer as well. */
    EXTERNAL
}
