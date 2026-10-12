package com.itsm.backend.ticket.dto;

import java.time.Instant;
import java.util.Map;

import com.itsm.backend.ticket.domain.TicketActivity;
import com.itsm.backend.ticket.domain.TicketActivityType;

/**
 * @param oldValue  user id (ASSIGNEE_CHANGED) or enum name; null = unassigned
 * @param oldName   display name for an assignee id or group name for a group id; null otherwise
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

    /**
     * @param names      display names by user id
     * @param groupNames support group names by id (for GROUP_CHANGED)
     */
    public static ActivityResponse from(TicketActivity activity, Map<String, String> names,
            Map<Long, String> groupNames) {
        if (activity.getType() == TicketActivityType.GROUP_CHANGED) {
            return new ActivityResponse(activity.getId(), activity.getType(), activity.getOldValue(),
                    groupName(activity.getOldValue(), groupNames), activity.getNewValue(),
                    groupName(activity.getNewValue(), groupNames), activity.getNote(), activity.getActorId(),
                    names.get(activity.getActorId()), activity.getCreatedAt());
        }
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

    /** A deleted group has no name any more: null, the client shows a placeholder. */
    private static String groupName(String id, Map<Long, String> groupNames) {
        return id == null ? null : groupNames.get(Long.valueOf(id));
    }
}
