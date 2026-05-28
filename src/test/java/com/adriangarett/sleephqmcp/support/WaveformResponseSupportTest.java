package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.WaveformWindowPlan;
import com.adriangarett.sleephqmcp.domain.WindowEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaveformResponseSupportTest {

    @Test
    void attachWindowSelection_addsNode() {
        String waveformJson = """
                {"filename":"BRP.edf","start_datetime":"2026-05-19T00:00:00","channels":[]}
                """;
        WaveformWindowPlan plan = new WaveformWindowPlan(
                "auto",
                "eve_scan_overlap",
                92,
                5520,
                5,
                15,
                "EVE hypopnea aligned with scan at 04:31",
                List.of(new WindowEvidence("get-device-events", "Hypopnea", 5820.0, "2026-05-19T04:31:00")),
                null);

        String merged = WaveformResponseSupport.attachWindowSelection(
                waveformJson, plan, new com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties(10, 60, 4000, 45, null));
        JsonNode root = JsonApi.parse(merged);

        assertThat(root.path("filename").asText()).isEqualTo("BRP.edf");
        JsonNode sel = root.path("window_selection");
        assertThat(sel.path("anchor_resolved").asText()).isEqualTo("eve_scan_overlap");
        assertThat(sel.path("start_minute").asInt()).isEqualTo(92);
        assertThat(sel.path("evidence").size()).isEqualTo(1);
        assertThat(sel.path("evidence").get(0).path("label").asText()).isEqualTo("Hypopnea");
    }
}
