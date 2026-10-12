package com.itsm.backend.group;

import java.util.List;
import java.util.Map;

import com.itsm.backend.group.GroupDtos.GroupRequest;
import com.itsm.backend.group.GroupDtos.GroupResponse;
import com.itsm.backend.group.GroupDtos.MembersRequest;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.ticket.domain.TicketCategory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groups;

    public GroupController(GroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    public List<GroupResponse> list(CurrentUser user) {
        return groups.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(CurrentUser user, @Valid @RequestBody GroupRequest request) {
        return groups.create(user, request);
    }

    @PutMapping("/{id}")
    public GroupResponse update(CurrentUser user, @PathVariable long id, @Valid @RequestBody GroupRequest request) {
        return groups.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser user, @PathVariable long id) {
        groups.delete(user, id);
    }

    @PutMapping("/{id}/members")
    public GroupResponse members(CurrentUser user, @PathVariable long id, @Valid @RequestBody MembersRequest request) {
        return groups.replaceMembers(user, id, request.userIds());
    }

    /** Body: {"NETWORK": 3, "OTHER": null, ...}; the whole table is replaced. */
    @PutMapping("/routes")
    public Map<TicketCategory, Long> routes(CurrentUser user, @RequestBody Map<TicketCategory, Long> routes) {
        return groups.replaceRoutes(user, routes);
    }
}
