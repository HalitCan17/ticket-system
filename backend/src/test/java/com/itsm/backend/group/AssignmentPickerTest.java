package com.itsm.backend.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AssignmentPickerTest {

    static final List<String> MEMBERS = List.of("c", "a", "b"); // order of input does not matter

    @Test
    void noneAndEmptyGroupsAssignNobody() {
        assertThat(AssignmentPicker.pick(AssignmentMode.NONE, MEMBERS, null, Map.of())).isEmpty();
        assertThat(AssignmentPicker.pick(AssignmentMode.ROUND_ROBIN, List.of(), null, Map.of())).isEmpty();
        assertThat(AssignmentPicker.pick(AssignmentMode.LEAST_LOADED, List.of(), "a", Map.of())).isEmpty();
    }

    @Test
    void roundRobinTakesTurnsInIdOrderAndWrapsAround() {
        assertThat(AssignmentPicker.pick(AssignmentMode.ROUND_ROBIN, MEMBERS, null, Map.of())).contains("a");
        assertThat(AssignmentPicker.pick(AssignmentMode.ROUND_ROBIN, MEMBERS, "a", Map.of())).contains("b");
        assertThat(AssignmentPicker.pick(AssignmentMode.ROUND_ROBIN, MEMBERS, "b", Map.of())).contains("c");
        assertThat(AssignmentPicker.pick(AssignmentMode.ROUND_ROBIN, MEMBERS, "c", Map.of())).contains("a");
    }

    @Test
    void roundRobinContinuesAfterAMemberWhoLeft() {
        // "b" left the group after getting the last ticket: the next one goes to whoever comes after "b".
        assertThat(AssignmentPicker.pick(AssignmentMode.ROUND_ROBIN, List.of("a", "c"), "b", Map.of())).contains("c");
        assertThat(AssignmentPicker.pick(AssignmentMode.ROUND_ROBIN, List.of("a", "c"), "z", Map.of())).contains("a");
    }

    @Test
    void leastLoadedPicksTheFewestOpenTicketsAndBreaksTiesByTurn() {
        Map<String, Long> load = Map.of("a", 3L, "b", 1L, "c", 1L);

        assertThat(AssignmentPicker.pick(AssignmentMode.LEAST_LOADED, MEMBERS, null, load)).contains("b");
        assertThat(AssignmentPicker.pick(AssignmentMode.LEAST_LOADED, MEMBERS, "b", load)).contains("c");
        // A member missing from the counts has no open work.
        assertThat(AssignmentPicker.pick(AssignmentMode.LEAST_LOADED, List.of("a", "d"), null, Map.of("a", 1L)))
                .contains("d");
    }

    @Test
    void turnOrderIgnoresDuplicates() {
        assertThat(AssignmentPicker.turnOrder(List.of("b", "a", "b"), "a")).containsExactly("b", "a");
    }
}
