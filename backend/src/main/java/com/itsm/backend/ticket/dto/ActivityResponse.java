package com.itsm.backend.ticket.dto;

import java.time.Instant;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketActivity;
import com.itsm.backend.ticket.domain.TicketActivityType;

/**
 * @param oldValue  user id (ASSIGNEE_CHANGED) or enum name; null = unassigned
 * @param oldName   display name for an assignee id; null otherwise
 */
public record ActivityResponse(
        Long id,
        TicketActivityType type,
        String oldValue,
        String oldName,
        String newValue,
        String newName,
        String note,
        String actorId,
        String actorName,
        Instant createdAt) {

    /** @param names display names by user id */
    public static ActivityResponse from(TicketActivity activity, Map<String, String> names) {
        boolean users = activity.getType() == TicketActivityType.ASSIGNEE_CHANGED;
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getOldValue(),
                users ? names.get(activity.getOldValue()) : null,
                activity.getNewValue(),
                users ? names.get(activity.getNewValue()) : null,
                activity.getNote(),
                activity.getActorId(),
                names.get(activity.getActorId()),
                activity.getCreatedAt());
    }
}
