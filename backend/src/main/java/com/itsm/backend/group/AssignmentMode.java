package com.itsm.backend.group;

/** How a support group assigns new tickets; see docs/adr/0014-support-groups.md. */
public enum AssignmentMode {
    /** Tickets wait in the group's queue until someone claims or assigns them. */
    NONE,
    /** Members take turns, in a stable order. */
    ROUND_ROBIN,
    /** The member with the fewest open tickets; ties go round-robin. */
    LEAST_LOADED
}
