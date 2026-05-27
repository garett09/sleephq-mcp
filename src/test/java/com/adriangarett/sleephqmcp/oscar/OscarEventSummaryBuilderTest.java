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

    @Test
    void buildSummary_prefersSummaryCountsWhenEveOnlyHasRecordingStart() {
        DeviceEventResult eve = new DeviceEventResult(
                "EVE.edf",
                "2026-05-18T21:00:00",
                28_800,
                "device_eve",
                List.of(new DeviceEvent("00:00:00", 0.0, 0.0, "2026-05-18T21:00:00", "Recording Start", "RS")));
        Map<String, Integer> summary = Map.of("clear_airway", 3, "hypopnea", 5);

        JsonNode node = OscarEventSummaryBuilder.buildSummary(eve, 100, Optional.of(summary));

        assertThat(node.get("eve_total").asInt()).isZero();
        assertThat(node.get("summary_total").asInt()).isEqualTo(8);
        assertThat(node.get("total").asInt()).isEqualTo(8);
        assertThat(node.get("event_count_authority").asText()).isEqualTo("oscar_summary_000");
        assertThat(node.get("counts").isEmpty()).isTrue();
    }

    @Test
    void isNonTherapyEvent_recognizesRecordingMarkers() {
        assertThat(OscarEventSummaryBuilder.isNonTherapyEvent("recording_start")).isTrue();
        assertThat(OscarEventSummaryBuilder.isNonTherapyEvent("recording_starts")).isTrue();
        assertThat(OscarEventSummaryBuilder.isNonTherapyEvent("hypopnea")).isFalse();
    }

    @Test
    void buildSummary_may21StyleEve_prefersSummaryTotal() {
        DeviceEventResult eve = new DeviceEventResult(
                "EVE.edf",
                "2026-05-20T21:00:00",
                28_800,
                "device_eve",
                List.of(
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
}
