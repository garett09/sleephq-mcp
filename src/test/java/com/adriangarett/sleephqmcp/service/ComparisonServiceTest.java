package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    @Mock
    private CombinedNightService combinedNightService;

    @Mock
    private JournalLookupService journalLookup;

    private ComparisonService service;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-1", "cpap-default", "o2-default", null);
        executor = Executors.newSingleThreadExecutor();
        SleepHqPayloadProperties payload = new SleepHqPayloadProperties(10, 60, 4000, 45, null);
        service = new ComparisonService(combinedNightService, journalLookup, clinical, executor, payload);
    }

    @Test
    void compare_twoDays_aggregatesNightsAndMeta() {
        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"ahi_summary\":{\"av\":1.0,\"oa\":0.5,\"ca\":0.2}}}}");
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-02"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"b\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"ahi_summary\":{\"av\":2.0,\"oa\":1.5,\"ca\":6.0}}}}");

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
        assertThat(root.path("meta").path("table_display_hint").asText()).contains("titration_decision_support");
        assertThat(root.path("mcp_payload_hints").path("waveform_default_max_minutes").asInt()).isEqualTo(10);
        assertThat(root.path("titration_readiness").path("nights_with_ahi_summary").asInt()).isEqualTo(2);
        assertThat(root.path("titration_readiness").path("ready_for_span_trends").asBoolean()).isTrue();
        assertThat(root.path("apnea_trends").path("nights_with_ahi_summary").asInt()).isEqualTo(2);
        assertThat(root.path("apnea_trends").path("titration_decision_support").path("evaluate_in_order").size()).isGreaterThan(0);
        assertThat(root.path("nights").get(0).path("table_display").path("osa_cell").asText()).isEqualTo("0.5/hr");
        assertThat(root.path("nights").get(1).path("table_display").path("csa_cell").asText()).isEqualTo("6/hr");
        assertThat(root.path("nights").get(1).path("table_display").path("csa_elevated").asBoolean()).isTrue();

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
    void compare_decisionGuardrails_mustNotIncreaseWhenCaRising() {
        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        // night 1 (prior): low CA
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"ahi_summary\":{\"av\":1.0,\"oa\":0.5,\"ca\":0.2}}}}");
        // night 2 (recent): CA elevated and rising
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-02"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"b\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"ahi_summary\":{\"av\":7.0,\"oa\":0.5,\"ca\":6.5}}}}");

        String json = service.compare("cpap-1", "2026-05-01", "2026-05-02");
        var root = JsonApi.parse(json);

        assertThat(root.path("decision_guardrails").path("must_not_increase_pressure").asBoolean()).isTrue();
        assertThat(root.path("decision_guardrails").path("must_not_increase_reason").asText()).contains("CA is rising");
        assertThat(root.path("decision_guardrails").path("ca_status").asText()).isEqualTo("rising");
        assertThat(root.path("decision_guardrails").path("mask_fit_check_required").asBoolean()).isFalse();
    }

    @Test
    void compare_decisionGuardrails_allowsIncreaseWhenOaRisingNoCa() {
        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        // night 1 (prior): low OA and CA
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"ahi_summary\":{\"av\":1.0,\"oa\":0.5,\"ca\":0.2}}}}");
        // night 2 (recent): OA rising, CA still normal
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-02"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"b\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"ahi_summary\":{\"av\":3.0,\"oa\":2.5,\"ca\":0.1}}}}");

        String json = service.compare("cpap-1", "2026-05-01", "2026-05-02");
        var root = JsonApi.parse(json);

        assertThat(root.path("decision_guardrails").path("must_not_increase_pressure").asBoolean()).isFalse();
        assertThat(root.path("decision_guardrails").path("must_not_increase_reason").asText()).isEmpty();
        assertThat(root.path("decision_guardrails").path("oa_status").asText()).isEqualTo("rising");
        assertThat(root.path("decision_guardrails").path("ca_status").asText()).isEqualTo("stable");
    }

    @Test
    void compare_noO2InMeta_whenClinicalO2Unset() {
        ClinicalContextProperties clinical = new ClinicalContextProperties(null, "x", null, null);
        SleepHqPayloadProperties payload = new SleepHqPayloadProperties(10, 60, 4000, 45, null);
        ComparisonService bare = new ComparisonService(combinedNightService, journalLookup, clinical, executor, payload);

        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        when(combinedNightService.combineForCalendarDateWithJournalMap(eq("2026-05-01"), eq("cpap-1"), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"a\",\"type\":\"machine_date\"}}");

        String json = bare.compare("cpap-1", "2026-05-01", "2026-05-01");
        var root = JsonApi.parse(json);

        assertThat(root.path("meta").path("o2_machine_id").isMissingNode()).isTrue();
    }

    @Test
    void compare_blockedAction_mentionsRawAhiSummaryInspection_whenNoNightsHaveAhi() {
        when(journalLookup.loadByDateRange(isNull(), any(), any())).thenReturn(java.util.Map.of());
        when(combinedNightService.combineForCalendarDateWithJournalMap(any(), any(), isNull(), any()))
                .thenReturn("{\"data\":{\"id\":\"x\",\"type\":\"machine_date\",\"attributes\":"
                        + "{\"ahi_summary\":{\"unknown_key\":1.0},\"usage\":28800}}}");

        String json = service.compare("cpap-1", "2026-05-01", "2026-05-01");
        JsonNode root = JsonApi.parse(json);

        assertThat(root.path("titration_readiness").path("nights_with_ahi_summary").asInt()).isEqualTo(0);
        String blockedAction = root.path("titration_readiness").path("blocked_action").asText();
        assertThat(blockedAction).contains("nights[0].data.attributes.ahi_summary");
        assertThat(blockedAction).contains("actual keys");
    }
}
