package com.itsm.backend.ticket.domain;

/** List tabs: everything not closed, only closed, or all tickets. */
public enum TicketScope {
    OPEN,
    CLOSED,
    ALL
}
