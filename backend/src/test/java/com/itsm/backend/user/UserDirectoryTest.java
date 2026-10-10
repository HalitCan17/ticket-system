package com.itsm.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.itsm.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserDirectoryTest {

    @Autowired
    UserDirectory directory;

    @Autowired
    JdbcTemplate jdbc;

    final String id = "user-" + UUID.randomUUID();

    @Test
    void remembersAndUpdatesProfile() {
        directory.remember(new UserProfile(id, "ayse", "Ayşe Yılmaz", "ayse@itsm.local", "CUSTOMER"));
        assertThat(directory.displayNames(List.of(id))).containsEntry(id, "Ayşe Yılmaz");

        directory.remember(new UserProfile(id, "ayse", "Ayşe Kaya", "ayse@itsm.local", "CUSTOMER"));
        assertThat(directory.displayNames(List.of(id))).containsEntry(id, "Ayşe Kaya");
    }

    @Test
    void unchangedProfileIsNotWrittenAgain() {
        UserProfile profile = new UserProfile(id, "mehmet", "Mehmet Demir", null, "CUSTOMER");
        directory.remember(profile);
        Timestamp firstWrite = updatedAt();

        directory.remember(new UserProfile(id, "mehmet", "Mehmet Demir", null, "CUSTOMER"));

        assertThat(updatedAt()).isEqualTo(firstWrite);
    }

    @Test
    void fallsBackToUsernameAndSkipsUnknownIds() {
        directory.remember(new UserProfile(id, "agent7", null, null, "CUSTOMER"));

        assertThat(directory.displayNames(Arrays.asList(id, "unknown-id", null)))
                .containsExactlyEntriesOf(Map.of(id, "agent7"));
    }

    @Test
    void knowsManagersByTheirLastRole() {
        directory.remember(new UserProfile(id, "boss", "Boss", null, "MANAGER"));
        assertThat(directory.managerIds()).contains(id);

        directory.remember(new UserProfile(id, "boss", "Boss", null, "AGENT"));
        assertThat(directory.managerIds()).doesNotContain(id);
    }

    @Test
    void systemActorHasAFixedName() {
        assertThat(directory.displayNames(List.of(UserDirectory.SYSTEM_USER_ID)))
                .containsEntry(UserDirectory.SYSTEM_USER_ID, "System");
    }

    private Timestamp updatedAt() {
        return jdbc.queryForObject("select updated_at from app_users where id = ?", Timestamp.class, id);
    }
}
