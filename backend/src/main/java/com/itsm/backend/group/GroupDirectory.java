package com.itsm.backend.group;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.itsm.backend.ticket.domain.TicketCategory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Small read/write queries around groups that do not need the entity (routes, names, membership, load). */
@Component
public class GroupDirectory {

    private final NamedParameterJdbcTemplate jdbc;

    public GroupDirectory(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Category → group id; categories without a route are absent. */
    public Map<TicketCategory, Long> routes() {
        Map<TicketCategory, Long> routes = new EnumMap<>(TicketCategory.class);
        jdbc.query("select category, group_id from category_routes", new MapSqlParameterSource(),
                rs -> {
                    routes.put(TicketCategory.valueOf(rs.getString("category")), rs.getLong("group_id"));
                });
        return routes;
    }

    public Long groupFor(TicketCategory category) {
        List<Long> ids = jdbc.queryForList("select group_id from category_routes where category = :category",
                new MapSqlParameterSource("category", category.name()), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Replaces the whole routing table; a null group removes the category's route. */
    public void replaceRoutes(Map<TicketCategory, Long> routes) {
        jdbc.update("delete from category_routes", new MapSqlParameterSource());
        routes.forEach((category, groupId) -> {
            if (groupId != null) {
                jdbc.update("insert into category_routes (category, group_id) values (:category, :groupId)",
                        new MapSqlParameterSource("category", category.name()).addValue("groupId", groupId));
            }
        });
    }

    /** Group name per id for the given ids; unknown ids are absent. */
    public Map<Long, String> names(Collection<Long> ids) {
        Set<Long> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (wanted.isEmpty()) {
            return names;
        }
        jdbc.query("select id, name from support_groups where id in (:ids)", new MapSqlParameterSource("ids", wanted),
                rs -> {
                    names.put(rs.getLong("id"), rs.getString("name"));
                });
        return names;
    }

    /** Ids of the groups the user is a member of. */
    public List<Long> groupIdsOf(String userId) {
        return jdbc.queryForList("select group_id from support_group_members where user_id = :userId order by group_id",
                new MapSqlParameterSource("userId", userId), Long.class);
    }

    /** Members of the group who are still known staff (a role may have changed since they were added). */
    public List<String> assignableMembers(long groupId) {
        return jdbc.queryForList("""
                select m.user_id from support_group_members m
                join app_users u on u.id = m.user_id and u.role in ('AGENT', 'MANAGER')
                where m.group_id = :groupId
                order by m.user_id""",
                new MapSqlParameterSource("groupId", groupId), String.class);
    }

    /** Open (NEW / IN_PROGRESS / WAITING_FOR_CUSTOMER) tickets assigned to each of the users. */
    public Map<String, Long> openWork(Collection<String> userIds) {
        Map<String, Long> counts = new HashMap<>();
        if (userIds.isEmpty()) {
            return counts;
        }
        jdbc.query("""
                select assignee_id, count(*) as n from tickets
                where assignee_id in (:ids) and status in ('NEW', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER')
                group by assignee_id""",
                new MapSqlParameterSource("ids", userIds),
                rs -> {
                    counts.put(rs.getString("assignee_id"), rs.getLong("n"));
                });
        return counts;
    }
}
