package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NightChannelSummaryTest {

    @Test
    void serializes_withExplicitKeys_andOmitsNullMarkers() throws Exception {
        NightChannelSummary s = new NightChannelSummary(
                "cmH2O", 11.2, 10.6, 8.4, 4.0, 12.0, 8.6, 14400, null);

        String json = new ObjectMapper().writeValueAsString(s);

        assertThat(json).contains("\"p99\":11.2", "\"p95\":10.6", "\"median\":8.4",
                "\"min\":4.0", "\"max\":12.0", "\"avg\":8.6", "\"count\":14400", "\"unit\":\"cmH2O\"");
        assertThat(json).doesNotContain("markers");
    }

    @Test
    void serializes_markersWhenPresent() throws Exception {
        NightChannelSummary s = new NightChannelSummary(
                "L/min", 22, 12, 2, 0, 38, 4.1, 14400,
                Map.of("time_above_24_l_min_seconds", 180.0));
        String json = new ObjectMapper().writeValueAsString(s);
        assertThat(json).contains("\"markers\"", "time_above_24_l_min_seconds");
    }
}
