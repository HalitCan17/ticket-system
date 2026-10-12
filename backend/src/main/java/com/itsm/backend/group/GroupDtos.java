package com.itsm.backend.group;

import java.util.List;

import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.user.StaffMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes of {@code /api/groups}. */
public final class GroupDtos {

    private GroupDtos() {
    }

    public record GroupRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 1000) String description,
            @NotNull AssignmentMode assignmentMode) {
    }

    public record MembersRequest(@NotNull @Size(max = 200) List<@NotBlank String> userIds) {
    }

    /**
     * @param categories categories routed to this group
     * @param members    current members with names (unknown ids show the id as name)
     */
    public record GroupResponse(
            Long id,
            String name,
            String description,
            AssignmentMode assignmentMode,
            List<TicketCategory> categories,
            List<StaffMember> members) {
    }

    /**
     * @param releasedTickets open tickets taken from removed members and assigned again (or put back in the queue)
     */
    public record MembersResponse(GroupResponse group, int releasedTickets) {
    }
}
