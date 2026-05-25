package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    @Mock
    private CombinedNightService combinedNightService;

    private ComparisonService service;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties(null, "cpap-default", "o2-default");
        service = new ComparisonService(combinedNightService, clinical);
    }

    @Test
    void compare_twoDays_aggregatesNightsAndMeta() {
        when(combinedNightService.combineForCalendarDate(eq("2026-05-01"), eq("cpap-1"), isNull()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\",\"attributes\":{}}}");
        when(combinedNightService.combineForCalendarDate(eq("2026-05-02"), eq("cpap-1"), isNull()))
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

        verify(combinedNightService).combineForCalendarDate("2026-05-01", "cpap-1", null);
        verify(combinedNightService).combineForCalendarDate("2026-05-02", "cpap-1", null);
        verifyNoMoreInteractions(combinedNightService);
    }

    @Test
    void compare_skippedDay_setsReasonWithoutData() {
        when(combinedNightService.combineForCalendarDate(eq("2026-05-01"), eq("cpap-1"), isNull()))
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
        ClinicalContextProperties clinical = new ClinicalContextProperties(null, "x", null);
        ComparisonService bare = new ComparisonService(combinedNightService, clinical);

        when(combinedNightService.combineForCalendarDate(eq("2026-05-01"), eq("cpap-1"), isNull()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\"}}");

        String json = bare.compare("cpap-1", "2026-05-01", "2026-05-01");
        var root = JsonApi.parse(json);

        assertThat(root.path("meta").path("o2_machine_id").isMissingNode()).isTrue();
    }
}
