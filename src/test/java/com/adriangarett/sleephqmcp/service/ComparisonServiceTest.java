package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import com.adriangarett.sleephqmcp.support.PhaseTiming;
import com.adriangarett.sleephqmcp.support.SameThreadExecutorService;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    @Mock
    private CombinedNightService combinedNightService;

    @Mock
    private JournalLookupService journalLookup;

    private ComparisonService service;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-1", "cpap-default", "o2-default", null);
        service = new ComparisonService(combinedNightService, journalLookup, clinical,
                new SameThreadExecutorService(), new PhaseTiming(new com.adriangarett.sleephqmcp.config.SleepHqObservabilityProperties(false)));
    }

    @Test
    void compare_twoDays_aggregatesNightsAndMeta() {
        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\",\"attributes\":{}}}");
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-02"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"b\",\"type\":\"machine_date\",\"attributes\":{}}}");

        String json = service.compare("cpap-1", "2026-05-01", "2026-05-02");
        var root = JsonApi.parse(json);

        assertThat(root.path("meta").path("source").asText()).isEqualTo("sleephq-mcp/aggregated_range");
        assertThat(root.path("meta").path("cpap_machine_id").asText()).isEqualTo("cpap-1");
        assertThat(root.path("meta").path("o2_machine_id").asText()).isEqualTo("o2-default");
        assertThat(root.path("meta").path("from").asText()).isEqualTo("2026-05-01");
        assertThat(root.path("meta").path("to").asText()).isEqualTo("2026-05-02");
        assertThat(root.path("nights").isArray()).isTrue();
        assertThat(root.path("nights").size()).isEqualTo(2);
        assertThat(root.path("nights").get(0).path("date").asText()).isEqualTo("2026-05-01");
        assertThat(root.path("nights").get(0).path("data").path("id").asText()).isEqualTo("a");
        assertThat(root.path("nights").get(1).path("data").path("id").asText()).isEqualTo("b");

        verify(combinedNightService).combineForCalendarDateWithJournalMap("2026-05-01", "cpap-1", null, java.util.Map.of());
        verify(combinedNightService).combineForCalendarDateWithJournalMap("2026-05-02", "cpap-1", null, java.util.Map.of());
        verifyNoMoreInteractions(combinedNightService);
    }

    @Test
    void compare_includesJournalOnSkippedNightWhenNoMachineDate() {
        ObjectNode journalAttrs = JsonApi.mapper().createObjectNode();
        journalAttrs.put("date", "2026-05-01");
        journalAttrs.put("step_count", 3000);
        when(journalLookup.loadByDateRange(isNull(), any(), any()))
                .thenReturn(java.util.Map.of("2026-05-01", journalAttrs));
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenThrow(new IllegalStateException("No CPAP machine_date for date=2026-05-01 (HTTP 404)"));

        String json = service.compare("cpap-1", "2026-05-01", "2026-05-01");
        var row = JsonApi.parse(json).path("nights").get(0);

        assertThat(row.path("skipped").asBoolean()).isTrue();
        assertThat(row.path("journal").path("step_count").asInt()).isEqualTo(3000);
    }

    @Test
    void compare_skippedDay_setsReasonWithoutData() {
        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenThrow(new IllegalStateException("No CPAP machine_date for date=2026-05-01 (HTTP 404)"));

        String json = service.compare("cpap-1", "2026-05-01", "2026-05-01");
        var root = JsonApi.parse(json);

        assertThat(root.path("nights").size()).isEqualTo(1);
        assertThat(root.path("nights").get(0).path("skipped").asBoolean()).isTrue();
        assertThat(root.path("nights").get(0).path("reason").asText()).contains("No CPAP");
        assertThat(root.path("nights").get(0).path("data").isMissingNode()).isTrue();
    }

    @Test
    void compare_fromAfterTo_throws() {
        assertThatThrownBy(() -> service.compare("cpap-1", "2026-05-02", "2026-05-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromDate");
    }

    @Test
    void compare_rangeExceedsMax_throws() {
        assertThatThrownBy(() -> service.compare("cpap-1", "2026-01-01", "2026-05-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120");
    }

    @Test
    void compare_noO2InMeta_whenClinicalO2Unset() {
        ClinicalContextProperties clinical = new ClinicalContextProperties(null, "x", null, null);
        ComparisonService bare = new ComparisonService(combinedNightService, journalLookup, clinical,
                new SameThreadExecutorService(), new PhaseTiming(new com.adriangarett.sleephqmcp.config.SleepHqObservabilityProperties(false)));

        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\"}}");

        String json = bare.compare("cpap-1", "2026-05-01", "2026-05-01");
        var root = JsonApi.parse(json);

        assertThat(root.path("meta").path("o2_machine_id").isMissingNode()).isTrue();
    }
}
