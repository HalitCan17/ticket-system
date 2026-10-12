package com.itsm.backend.user;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Local copy of names of users seen in access tokens (table {@code app_users}), so ticket data that
 * stores user ids can be shown with names. Keycloak remains the source of truth.
 */
@Component
public class UserDirectory {

    /** Actor id used for changes made by the application itself (e.g. auto-close). */
    public static final String SYSTEM_USER_ID = "system";
    static final String SYSTEM_DISPLAY_NAME = "System";

    private static final Logger log = LoggerFactory.getLogger(UserDirectory.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    /** Last profile written per user; skips the write when nothing changed. Bounded by the number of users. */
    private final ConcurrentMap<String, UserProfile> lastWritten = new ConcurrentHashMap<>();

    public UserDirectory(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Records the profile if it differs from what was last written. Failures are logged, not thrown:
     * a missing name must never fail the actual request.
     */
    public void remember(UserProfile profile) {
        if (profile.equals(lastWritten.get(profile.id()))) {
            return;
        }
        try {
            jdbc.update("""
                    insert into app_users (id, username, display_name, email, role, updated_at)
                    values (:id, :username, :displayName, :email, :role, :updatedAt)
                    on conflict (id) do update set
                        username = excluded.username,
                        display_name = excluded.display_name,
                        email = excluded.email,
                        role = excluded.role,
                        updated_at = excluded.updated_at""",
                    new MapSqlParameterSource()
                            .addValue("id", profile.id())
                            .addValue("username", profile.username())
                            .addValue("displayName", profile.displayName())
                            .addValue("email", profile.email())
                            .addValue("role", profile.role())
                            .addValue("updatedAt", Timestamp.from(clock.instant())));
            lastWritten.put(profile.id(), profile);
        } catch (RuntimeException e) {
            log.warn("Could not record user {} in the directory", profile.id(), e);
        }
    }

    /**
     * Users whose last token carried the MANAGER role. Only users who signed in at least once are known
     * here; see docs/adr/0007-in-app-notifications.md.
     */
    public List<String> managerIds() {
        return jdbc.queryForList("select id from app_users where role = 'MANAGER' order by id",
                new MapSqlParameterSource(), String.class);
    }

    /** Known agents and managers, by name. Only users who signed in at least once are listed. */
    public List<StaffMember> staffMembers() {
        return jdbc.query("""
                select id, coalesce(display_name, username, id) as name, role from app_users
                where role in ('AGENT', 'MANAGER')
                order by lower(coalesce(display_name, username, id)), id""",
                new MapSqlParameterSource(),
                (rs, i) -> new StaffMember(rs.getString("id"), rs.getString("name"), rs.getString("role")));
    }

    /** Whether the id belongs to a known agent or manager. */
    public boolean isStaff(String id) {
        Integer count = jdbc.queryForObject(
                "select count(*) from app_users where id = :id and role in ('AGENT', 'MANAGER')",
                new MapSqlParameterSource("id", id), Integer.class);
        return count != null && count > 0;
    }

    /** Display name (or username) per known id; unknown ids are absent from the result. */
    public Map<String, String> displayNames(Collection<String> ids) {
        Set<String> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        if (wanted.remove(SYSTEM_USER_ID)) {
            names.put(SYSTEM_USER_ID, SYSTEM_DISPLAY_NAME);
        }
        if (wanted.isEmpty()) {
            return names;
        }
        jdbc.query("select id, coalesce(display_name, username) as name from app_users where id in (:ids)",
                new MapSqlParameterSource("ids", wanted),
                rs -> {
                    String name = rs.getString("name");
                    if (name != null) {
                        names.put(rs.getString("id"), name);
                    }
                });
        return names;
    }
}
