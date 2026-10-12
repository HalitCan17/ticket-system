package com.itsm.backend.group;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itsm.backend.common.error.BadRequestException;
import com.itsm.backend.common.error.ConflictException;
import com.itsm.backend.common.error.ForbiddenException;
import com.itsm.backend.common.error.NotFoundException;
import com.itsm.backend.group.GroupDtos.GroupRequest;
import com.itsm.backend.group.GroupDtos.GroupResponse;
import com.itsm.backend.security.CurrentUser;
import com.itsm.backend.security.Role;
import com.itsm.backend.ticket.domain.TicketCategory;
import com.itsm.backend.user.StaffMember;
import com.itsm.backend.user.UserDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff list groups; only managers change groups, members and routes. */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final SupportGroupRepository groups;
    private final GroupDirectory directory;
    private final UserDirectory userDirectory;
    private final Clock clock;

    public GroupService(SupportGroupRepository groups, GroupDirectory directory, UserDirectory userDirectory,
            Clock clock) {
        this.groups = groups;
        this.directory = directory;
        this.userDirectory = userDirectory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(CurrentUser user) {
        if (!user.isStaff()) {
            throw new ForbiddenException("STAFF_ONLY", "Only staff can see support groups.");
        }
        List<SupportGroup> all = groups.findAllByName();
        Map<TicketCategory, Long> routes = directory.routes();
        Set<String> memberIds = new LinkedHashSet<>();
        all.forEach(g -> memberIds.addAll(g.getMemberIds()));
        Map<String, String> names = userDirectory.displayNames(memberIds);
        Map<String, String> roles = new HashMap<>();
        userDirectory.staffMembers().forEach(m -> roles.put(m.id(), m.role()));
        return all.stream().map(g -> toResponse(g, routes, names, roles)).toList();
    }

    @Transactional
    public GroupResponse create(CurrentUser user, GroupRequest request) {
        requireManager(user);
        String name = request.name().strip();
        checkNameFree(name, -1);
        SupportGroup group = save(new SupportGroup(name, blankToNull(request.description()), request.assignmentMode(),
                clock.instant()));
        log.info("Support group {} created by {}", group.getId(), user.id());
        return single(group);
    }

    @Transactional
    public GroupResponse update(CurrentUser user, long id, GroupRequest request) {
        requireManager(user);
        SupportGroup group = find(id);
        String name = request.name().strip();
        checkNameFree(name, id);
        group.update(name, blankToNull(request.description()), request.assignmentMode(), clock.instant());
        save(group);
        log.info("Support group {} updated by {} (mode {})", id, user.id(), request.assignmentMode());
        return single(group);
    }

    /** Tickets of the group become group-less (FK ON DELETE SET NULL); their assignees stay. */
    @Transactional
    public void delete(CurrentUser user, long id) {
        requireManager(user);
        groups.delete(find(id));
        log.info("Support group {} deleted by {}", id, user.id());
    }

    @Transactional
    public GroupResponse replaceMembers(CurrentUser user, long id, List<String> userIds) {
        requireManager(user);
        SupportGroup group = find(id);
        Set<String> members = new LinkedHashSet<>();
        for (String userId : userIds) {
            String trimmed = userId.strip();
            if (!userDirectory.isStaff(trimmed)) {
                throw new BadRequestException("MEMBER_NOT_STAFF", "Only agents and managers can be group members.");
            }
            members.add(trimmed);
        }
        group.replaceMembers(members, clock.instant());
        save(group);
        log.info("Support group {} now has {} member(s), changed by {}", id, members.size(), user.id());
        return single(group);
    }

    /** Replaces the whole routing table; categories missing from the request lose their route. */
    @Transactional
    public Map<TicketCategory, Long> replaceRoutes(CurrentUser user, Map<TicketCategory, Long> routes) {
        requireManager(user);
        Map<TicketCategory, Long> cleaned = new EnumMap<>(TicketCategory.class);
        routes.forEach((category, groupId) -> {
            if (groupId != null) {
                if (!groups.existsById(groupId)) {
                    throw new BadRequestException("GROUP_NOT_FOUND", "Support group " + groupId + " does not exist.");
                }
                cleaned.put(category, groupId);
            }
        });
        directory.replaceRoutes(cleaned);
        log.info("Category routes replaced by {}: {}", user.id(), cleaned);
        return directory.routes();
    }

    private GroupResponse single(SupportGroup group) {
        Map<String, String> roles = new HashMap<>();
        userDirectory.staffMembers().forEach(m -> roles.put(m.id(), m.role()));
        return toResponse(group, directory.routes(), userDirectory.displayNames(group.getMemberIds()), roles);
    }

    private static GroupResponse toResponse(SupportGroup group, Map<TicketCategory, Long> routes,
            Map<String, String> names, Map<String, String> roles) {
        List<TicketCategory> categories = new ArrayList<>();
        routes.forEach((category, groupId) -> {
            if (groupId.equals(group.getId())) {
                categories.add(category);
            }
        });
        List<StaffMember> members = group.getMemberIds().stream()
                .map(id -> new StaffMember(id, names.getOrDefault(id, id), roles.get(id)))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
        return new GroupResponse(group.getId(), group.getName(), group.getDescription(), group.getAssignmentMode(),
                categories, members);
    }

    private SupportGroup find(long id) {
        return groups.findById(id)
                .orElseThrow(() -> new NotFoundException("GROUP_NOT_FOUND", "Support group " + id + " not found."));
    }

    private void checkNameFree(String name, long exceptId) {
        if (groups.nameTaken(name, exceptId)) {
            throw nameTaken();
        }
    }

    /** The unique index decides when two managers save the same name at once. */
    private SupportGroup save(SupportGroup group) {
        try {
            return groups.saveAndFlush(group);
        } catch (DataIntegrityViolationException e) {
            throw nameTaken();
        }
    }

    private static ConflictException nameTaken() {
        return new ConflictException("GROUP_NAME_TAKEN", "A support group with this name already exists.");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void requireManager(CurrentUser user) {
        if (user.role() != Role.MANAGER) {
            throw new ForbiddenException("MANAGER_ONLY", "Only managers can change support groups.");
        }
    }
}
