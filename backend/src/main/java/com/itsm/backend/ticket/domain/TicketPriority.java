package com.itsm.backend.ticket.domain;

public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** The next more urgent priority; CRITICAL stays CRITICAL. */
    public TicketPriority higher() {
        TicketPriority[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : this;
    }
}
