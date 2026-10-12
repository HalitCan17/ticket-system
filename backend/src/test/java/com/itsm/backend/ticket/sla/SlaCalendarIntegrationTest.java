package com.itsm.backend.ticket.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsm.backend.TestTokens;
import com.itsm.backend.TestcontainersConfiguration;
import com.itsm.backend.ticket.domain.BusinessCalendar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Aşama 15b: the business calendar (docs/adr/0015-sla-extensions.md). It is global, so every test restores it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SlaCalendarIntegrationTest {

    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    BusinessCalendarStore store;

    @Autowired
    BusinessCalendarProvider provider;

    @Autowired
    SlaBreachService monitor;

    final String customer = "cust-" + UUID.randomUUID().toString().substring(0, 8);
    BusinessCalendarStore.Settings saved;

    @BeforeEach
    void rememberCalendar() {
        saved = store.load();
    }

    @AfterEach
    void restoreCalendar() {
        store.save(saved, Instant.now());
        provider.invalidate();
    }

    @Test
    void defaultsAreSevenByTwentyFourWithFixedHolidays() throws Exception {
        perform(get("/api/sla/calendar"), "agent1", "AGENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.zoneId").value("Europe/Istanbul"))
                .andExpect(jsonPath("$.workDays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.workDays.length()").value(5))
                .andExpect(jsonPath("$.start").value("09:00"))
                .andExpect(jsonPath("$.end").value("18:00"))
                .andExpect(jsonPath("$.holidays[*].date", hasItem("2026-10-29")))
                .andExpect(jsonPath("$.resolutionSeconds.CRITICAL").value(4 * 3600))
                .andExpect(jsonPath("$.firstResponseSeconds.CRITICAL").value(30 * 60))
                .andExpect(jsonPath("$.raisePriorityOnBreach").value(false));
        perform(get("/api/sla/calendar"), customer, "CUSTOMER").andExpect(status().isForbidden());
    }

    @Test
    void onlyManagersSaveAndInputIsValidated() throws Exception {
        Map<String, Object> ok = request(true, List.of("MONDAY"), "09:00", "18:00", List.of());
        write(ok, "agent1", "AGENT").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MANAGER_ONLY"));
        write(ok, customer, "CUSTOMER").andExpect(status().isForbidden());

        write(withZone(ok, "Mars/Olympus"), "manager1", "MANAGER")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CALENDAR"));
        write(request(true, List.of("MONDAY"), "18:00", "09:00", List.of()), "manager1", "MANAGER")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CALENDAR"));
        write(request(true, List.of("MONDAY"), "9", "18:00", List.of()), "manager1", "MANAGER")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CALENDAR"));
        write(request(true, List.of(), "09:00", "18:00", List.of()), "manager1", "MANAGER")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CALENDAR"));
        write(request(true, List.of("MONDAY"), "09:00", "18:00",
                List.of(Map.of("date", "2026-12-24", "name", "A"), Map.of("date", "2026-12-24", "name", "B"))),
                "manager1", "MANAGER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CALENDAR"));
        write(request(true, List.of("MONDAY"), "09:00", "18:00", List.of(Map.of("date", "2026-12-24", "name", " "))),
                "manager1", "MANAGER").andExpect(status().isBadRequest());

        // Nothing was saved by the rejected requests.
        perform(get("/api/sla/calendar"), "manager1", "MANAGER").andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void savingReplacesSettingsAndHolidaysAndNewTicketsFollowTheCalendar() throws Exception {
        LocalDate today = ZonedDateTime.now(ISTANBUL).toLocalDate();
        Map<String, Object> body = request(true, List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY",
                "SATURDAY", "SUNDAY"), "09:00", "18:00", List.of(Map.of("date", today.toString(), "name", "Test tatili")));

        write(body, "manager1", "MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendar.enabled").value(true))
                .andExpect(jsonPath("$.calendar.workDays.length()").value(7))
                .andExpect(jsonPath("$.calendar.holidays.length()").value(1))
                .andExpect(jsonPath("$.calendar.holidays[0].name").value("Test tatili"));
        perform(get("/api/sla/calendar"), "agent1", "AGENT")
                .andExpect(jsonPath("$.holidays.length()").value(1)); // the fixed holidays were replaced

        BusinessCalendar calendar = new BusinessCalendar(true, ISTANBUL, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(18, 0), Set.of(today));
        long id = createTicket("HIGH"); // 8 h resolution, 1 h first response
        JsonNode ticket = ticket(id);
        Instant created = Instant.parse(ticket.get("createdAt").asText());

        assertThat(Instant.parse(ticket.at("/sla/dueAt").asText()))
                .isEqualTo(calendar.plus(created, Duration.ofHours(8)));
        assertThat(Instant.parse(ticket.at("/firstResponse/dueAt").asText()))
                .isEqualTo(calendar.plus(created, Duration.ofHours(1)));
        // Today is a holiday: nothing is due before tomorrow's opening.
        Instant tomorrowOpening = today.plusDays(1).atTime(9, 0).atZone(ISTANBUL).toInstant();
        assertThat(Instant.parse(ticket.at("/sla/dueAt").asText())).isAfter(tomorrowOpening);
    }

    @Test
    void savingRecalculatesOpenTicketsAndSwitchingBackRestoresRealHours() throws Exception {
        long id = createTicket("HIGH");
        JsonNode before = ticket(id);
        Instant created = Instant.parse(before.get("createdAt").asText());
        Instant dueBefore = Instant.parse(before.at("/sla/dueAt").asText());
        assertThat(dueBefore).isEqualTo(created.plus(Duration.ofHours(8)));

        LocalDate today = ZonedDateTime.now(ISTANBUL).toLocalDate();
        JsonNode result = json.readTree(write(request(true, List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                "FRIDAY", "SATURDAY", "SUNDAY"), "09:00", "18:00",
                List.of(Map.of("date", today.toString(), "name", "Bugün kapalı"))), "manager1", "MANAGER")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("recalculatedTickets").asInt()).isGreaterThanOrEqualTo(1);

        Instant tomorrowOpening = today.plusDays(1).atTime(9, 0).atZone(ISTANBUL).toInstant();
        Instant dueDuring = Instant.parse(ticket(id).at("/sla/dueAt").asText());
        // About 8 working hours from tomorrow's opening (a few seconds were already used).
        assertThat(dueDuring).isBetween(tomorrowOpening.plus(Duration.ofHours(8)).minusSeconds(120),
                tomorrowOpening.plus(Duration.ofHours(8)));

        // Disabled again: the clock counts real hours from the moment of the change.
        write(request(false, List.of("MONDAY"), "09:00", "18:00", List.of()), "manager1", "MANAGER")
                .andExpect(status().isOk());
        Instant dueAfter = Instant.parse(ticket(id).at("/sla/dueAt").asText());
        assertThat(dueAfter).isBetween(created.plus(Duration.ofHours(8)).minusSeconds(120),
                created.plus(Duration.ofHours(8)).plusSeconds(120));
    }

    @Test
    void breachDetectionFollowsTheBusinessCalendar() throws Exception {
        LocalDate today = ZonedDateTime.now(ISTANBUL).toLocalDate();
        write(request(true, List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                "09:00", "18:00", List.of(Map.of("date", today.toString(), "name", "Bugün kapalı"))),
                "manager1", "MANAGER").andExpect(status().isOk());
        long id = createTicket("CRITICAL"); // 4 working hours, none of them today
        Instant due = Instant.parse(ticket(id).at("/sla/dueAt").asText());
        Instant created = Instant.parse(ticket(id).get("createdAt").asText());

        // 6 real hours is long past the 4 h target in plain time, but no working hour has passed yet.
        monitor.notifyBreaches(created.plus(Duration.ofHours(6)));
        assertThat(ticket(id).get("escalatedAt").isNull()).isTrue();

        monitor.notifyBreaches(due.minusSeconds(60));
        assertThat(ticket(id).get("escalatedAt").isNull()).isTrue();

        monitor.notifyBreaches(due.plusSeconds(60));
        assertThat(ticket(id).get("escalatedAt").isNull()).isFalse();
    }

    private Map<String, Object> request(boolean enabled, List<String> days, String start, String end,
            List<Map<String, String>> holidays) {
        return Map.of("enabled", enabled, "zoneId", "Europe/Istanbul", "workDays", days, "start", start, "end", end,
                "holidays", holidays);
    }

    private Map<String, Object> withZone(Map<String, Object> request, String zone) {
        Map<String, Object> copy = new java.util.HashMap<>(request);
        copy.put("zoneId", zone);
        return copy;
    }

    private long createTicket(String priority) throws Exception {
        String body = json.writeValueAsString(Map.of("title", "Takvim " + customer, "description", "x",
                "priority", priority, "category", "OTHER"));
        return json.readTree(perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body),
                customer, "CUSTOMER").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private JsonNode ticket(long id) throws Exception {
        return json.readTree(perform(get("/api/tickets/{id}", id), "manager1", "MANAGER")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions write(Map<String, Object> body, String userId, String role) throws Exception {
        return perform(put("/api/sla/calendar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, role);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String userId, String role) throws Exception {
        return mvc.perform(request.with(TestTokens.user(userId, role)));
    }
}
