package com.adriangarett.sleephqmcp.domain;

import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.NightSummaryComputer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NightChannelSummaryTest {

    @Test
    void serializes_withExplicitKeys_andOmitsNullMarkers() throws Exception {
        NightChannelSummary s = new NightChannelSummary(
                "cmH2O", 11.2, 10.9, 10.6, 8.4, 4.0, 12.0, 8.6, 14400, null);

        String json = new ObjectMapper().writeValueAsString(s);

        assertThat(json).contains("\"p99\":11.2", "\"p99_5\":10.9", "\"p95\":10.6", "\"median\":8.4",
                "\"min\":4.0", "\"max\":12.0", "\"avg\":8.6", "\"count\":14400", "\"unit\":\"cmH2O\"");
        assertThat(json).doesNotContain("markers");
    }

    @Test
    void serializes_markersWhenPresent() throws Exception {
        NightChannelSummary s = new NightChannelSummary(
                "L/min", 22, 20, 12, 2, 0, 38, 4.1, 14400,
                Map.of("time_above_24_l_min_seconds", 180.0));
        String json = new ObjectMapper().writeValueAsString(s);
        assertThat(json).contains("\"markers\"", "time_above_24_l_min_seconds");
    }

    @Test
    void summarise_pressureSamples_jsonContainsp99_5Key() throws Exception {
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < 90; i++) samples.add(8.0);
        for (int i = 0; i < 10; i++) samples.add(12.0);

        NightChannelSummary s = NightSummaryComputer.summarise("pressure", "cmH2O", samples, 0.5);
        assertThat(s).isNotNull();

        String json = JsonApi.mapper().writeValueAsString(s);
        JsonNode node = JsonApi.mapper().readTree(json);

        assertThat(node.has("p99_5")).isTrue();
        assertThat(node.path("p99_5").asDouble()).isCloseTo(12.0, within(0.01));
        assertThat(node.has("p99")).isTrue();
        assertThat(node.has("p95")).isTrue();
        assertThat(node.has("median")).isTrue();
    }

    @Test
    void summarise_leakRate_p99_5_isPlausible() {
        List<Double> raw = new ArrayList<>();
        for (int i = 0; i < 95; i++) raw.add(0.1);  // 6 L/min after L/s conversion
        for (int i = 0; i < 5; i++) raw.add(0.5);   // 30 L/min after L/s conversion

        NightChannelSummary s = NightSummaryComputer.summarise("leak_rate", "L/s", raw, 0.5);
        assertThat(s.p995()).isCloseTo(30.0, within(0.01));
        assertThat(s.p995()).isGreaterThanOrEqualTo(s.p95());
    }
}
