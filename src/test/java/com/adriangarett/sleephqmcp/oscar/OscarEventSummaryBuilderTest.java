package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OscarEventSummaryBuilderTest {

    // ---------- helpers ----------

    private static DeviceEvent event(String label) {
        return new DeviceEvent("00:00:00", 0.0, 10.0, "2026-05-18T21:00:00", label, "XX");
    }

    private static DeviceEventResult result(List<DeviceEvent> events) {
        return new DeviceEventResult("EVE.edf", "2026-05-18T21:00:00", 28_800, "device_eve", events);
    }

    // ---------- existing: recording-only EVE with summary ----------

    @Test
    void buildSummary_prefersSummaryCountsWhenEveOnlyHasRecordingStart() {
        DeviceEventResult eve = result(List.of(
                new DeviceEvent("00:00:00", 0.0, 0.0, "2026-05-18T21:00:00", "Recording Start", "RS")));
        Map<String, Integer> summary = Map.of("clear_airway", 3, "hypopnea", 5);

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.of(summary));

        assertThat(node.get("eve_total").asInt()).isZero();
        assertThat(node.get("summary_total").asInt()).isEqualTo(8);
        assertThat(node.get("total").asInt()).isEqualTo(8);
        assertThat(node.get("event_count_authority").asText()).isEqualTo("oscar_summary_000");
        assertThat(node.get("counts").isEmpty()).isTrue();
    }

    // ---------- existing: may-21 style (updated to canonical keys) ----------

    @Test
    void buildSummary_may21StyleEve_prefersSummaryTotal() {
        DeviceEventResult eve = result(List.of(
                new DeviceEvent("00:00:00", 0.0, 0.0, "2026-05-20T21:09:12", "Recording starts", "RS"),
                new DeviceEvent("01:00:00", 3600.0, 10.0, "2026-05-20T22:09:12", "Central Apnea", "CA"),
                new DeviceEvent("02:00:00", 7200.0, 12.0, "2026-05-20T23:09:12", "Hypopnea", "H")));
        Map<String, Integer> summary = Map.of("clear_airway", 3, "hypopnea", 2);

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.of(summary));

        assertThat(node.get("eve_total").asInt()).isEqualTo(2);
        assertThat(node.get("summary_total").asInt()).isEqualTo(5);
        assertThat(node.get("total").asInt()).isEqualTo(5);
        assertThat(node.get("event_count_authority").asText()).isEqualTo("oscar_summary_000");
    }

    // ---------- NEW: canonical keys ----------

    @Test
    void eveLabelsAreCanonicalizedAndMatchSummaryKeys() {
        DeviceEventResult eve = result(List.of(
                event("Obstructive Apnea"),
                event("Central Apnea"),
                event("Hypopnea"),
                event("RERA")));
        Map<String, Integer> summary = Map.of("obstructive", 1, "clear_airway", 1, "hypopnea", 1, "rera", 1);

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.of(summary));

        JsonNode counts = node.get("counts");
        assertThat(counts.has("obstructive")).isTrue();
        assertThat(counts.has("clear_airway")).isTrue();
        assertThat(counts.has("hypopnea")).isTrue();
        assertThat(counts.has("rera")).isTrue();
        // old non-canonical keys must NOT be present
        assertThat(counts.has("obstructive_apnea")).isFalse();
        assertThat(counts.has("central_apnea")).isFalse();
    }

    // ---------- NEW: timed_sample excludes recording markers ----------

    @Test
    void timedSampleSkipsRecordingMarkers() {
        DeviceEventResult eve = result(List.of(
                event("Recording starts"),
                event("Hypopnea"),
                event("Recording ends")));

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.empty());

        JsonNode timed = node.get("timed_sample");
        assertThat(timed.size()).isEqualTo(1);
        assertThat(timed.get(0).get("label").asText()).isEqualTo("Hypopnea");
    }

    // ---------- NEW: authority + agree flag ----------

    @Test
    void authorityIsSummaryAndAgreeFlagTrueWhenTotalsMatch() {
        DeviceEventResult eve = result(List.of(
                event("Hypopnea"),
                event("Hypopnea")));
        Map<String, Integer> summary = Map.of("hypopnea", 2);

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.of(summary));

        assertThat(node.get("event_count_authority").asText()).isEqualTo("oscar_summary_000");
        assertThat(node.get("event_counts_agree").asBoolean()).isTrue();
    }

    @Test
    void authorityIsSummaryAndAgreeFlagFalseWhenTotalsDiffer() {
        DeviceEventResult eve = result(List.of(
                event("Hypopnea"),
                event("Hypopnea")));
        // summary says 5 total but EVE only counted 2
        Map<String, Integer> summary = Map.of("hypopnea", 5);

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.of(summary));

        assertThat(node.get("event_count_authority").asText()).isEqualTo("oscar_summary_000");
        assertThat(node.get("event_counts_agree").asBoolean()).isFalse();
    }

    @Test
    void authorityIsEveEdfWhenNoSummaryCountsAndEventsPresent() {
        DeviceEventResult eve = result(List.of(event("Hypopnea")));

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.empty());

        assertThat(node.get("event_count_authority").asText()).isEqualTo("oscar_eve_edf");
        assertThat(node.has("event_counts_agree")).isFalse();
    }
}
