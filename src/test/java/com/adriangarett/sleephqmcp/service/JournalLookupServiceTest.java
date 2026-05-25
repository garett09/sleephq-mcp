package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalLookupServiceTest {

    @Mock
    private SleepHqClient client;

    private JournalLookupService service;
    private String sampleListJson;

    @BeforeEach
    void setUp() throws Exception {
        service = new JournalLookupService(client, new ClinicalContextProperties("team-1", null, null));
        sampleListJson = new String(
                getClass().getResourceAsStream("/journal/list-journals-sample.json").readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    void loadByDateRange_indexesInRangeOnly() {
        when(client.listJournals(eq("team-1"), eq(1), eq(100))).thenReturn(sampleListJson);

        Map<String, JsonNode> map = service.loadByDateRange(null, LocalDate.parse("2026-05-23"),
                LocalDate.parse("2026-05-24"));

        assertThat(map).hasSize(2);
        assertThat(map.get("2026-05-23").path("step_count").asInt()).isEqualTo(8421);
        assertThat(map.get("2026-05-24").path("sleep_stages").asText()).contains("Light sleep");
    }

    @Test
    void loadByDateRange_singleDay_filtersOutside() {
        when(client.listJournals(eq("team-1"), eq(1), eq(100))).thenReturn(sampleListJson);

        Map<String, JsonNode> map = service.loadByDateRange("team-1", LocalDate.parse("2026-05-24"),
                LocalDate.parse("2026-05-24"));

        assertThat(map).hasSize(1);
        assertThat(map).containsKey("2026-05-24");
    }

    @Test
    void findAttributesByDate_hit_returnsAttributes() {
        when(client.listJournals(eq("team-1"), eq(1), eq(100))).thenReturn(sampleListJson);

        var found = service.findAttributesByDate("team-1", "2026-05-23");

        assertThat(found).isPresent();
        assertThat(found.get().path("active_energy_joules").asLong()).isEqualTo(1234000L);
    }

    @Test
    void requireTeamId_missingConfigured_throws() {
        JournalLookupService bare = new JournalLookupService(client, new ClinicalContextProperties(null, null, null));
        assertThatThrownBy(() -> bare.findAttributesByDate(null, "2026-05-23"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SLEEPHQ_TEAM_ID");
    }
}
