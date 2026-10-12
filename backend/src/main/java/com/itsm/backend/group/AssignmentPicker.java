package com.itsm.backend.group;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Chooses who gets an automatically assigned ticket. Pure Java, unit tested.
 *
 * <p>Members are taken in id order. Round-robin continues after the member who got the previous ticket
 * ({@code lastAssignedId}); if that member left the group, it continues with the next id after it.
 */
public final class AssignmentPicker {

    private AssignmentPicker() {
    }

    /**
     * @param members        current members who can be assigned (any order)
     * @param lastAssignedId round-robin cursor; null if nobody was assigned yet
     * @param openWork       open tickets per member (missing = 0); used by {@link AssignmentMode#LEAST_LOADED}
     * @return the member to assign, or empty for {@link AssignmentMode#NONE} or a group without members
     */
    public static Optional<String> pick(AssignmentMode mode, Collection<String> members, String lastAssignedId,
            Map<String, Long> openWork) {
        if (mode == AssignmentMode.NONE || members.isEmpty()) {
            return Optional.empty();
        }
        List<String> turnOrder = turnOrder(members, lastAssignedId);
        if (mode == AssignmentMode.ROUND_ROBIN) {
            return Optional.of(turnOrder.get(0));
        }
        // LEAST_LOADED: the first in turn order among those with the fewest open tickets.
        long fewest = turnOrder.stream().mapToLong(m -> openWork.getOrDefault(m, 0L)).min().orElse(0);
        return turnOrder.stream().filter(m -> openWork.getOrDefault(m, 0L) == fewest).findFirst();
    }

    /** Members sorted by id, rotated to start right after {@code lastAssignedId}. */
    static List<String> turnOrder(Collection<String> members, String lastAssignedId) {
        List<String> sorted = members.stream().distinct().sorted().toList();
        if (lastAssignedId == null) {
            return sorted;
        }
        int start = 0;
        while (start < sorted.size() && sorted.get(start).compareTo(lastAssignedId) <= 0) {
            start++;
        }
        if (start == sorted.size()) {
            return sorted;
        }
        return Stream.concat(sorted.subList(start, sorted.size()).stream(),
                sorted.subList(0, start).stream()).toList();
    }
}
