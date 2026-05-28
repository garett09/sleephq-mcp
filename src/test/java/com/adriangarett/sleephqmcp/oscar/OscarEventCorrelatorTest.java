package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OscarEventCorrelatorTest {

    @Test
    void notableMoment_usesSessionRelativeOffset_notWallClock() {
        // Session starts at 22:00; channel peak is 10 minutes in (offset 600s, wall clock 22:10:00).
        ChannelStatistics leak = new ChannelStatistics(
                "leak", "L/min", 10.0, 0.0, 42.0, 30.0, 20.0,
                "", "22:10:00", ChannelStatistics.OFFSET_UNKNOWN, 600, 100);
        // A device event 30s after the peak should correlate within the 120s window.
        DeviceEvent event = new DeviceEvent(
                "00:10:30", 630.0, 12.0, "2026-05-19T22:10:30", "Hypopnea", "H");

        List<ObjectNode> moments = OscarEventCorrelator.buildNotableMoments(
                Map.of("leak", leak),
                List.of(event),
                "2026-05-19T22:00:00",
                120,
                5,
                5);

        assertThat(moments).hasSize(1);
        ObjectNode moment = moments.get(0);
        assertThat(moment.path("offset_seconds").asInt()).isEqualTo(600);
        assertThat(moment.path("timestamp").asText()).isEqualTo("2026-05-19T22:10:00");
        assertThat(moment.path("nearby_events")).hasSize(1);
        assertThat(moment.path("nearby_events").get(0).path("delta_seconds").asLong()).isEqualTo(30L);
    }

    @Test
    void notableMoment_correlatesEventWithMinutePrecisionTimestamp() {
        ChannelStatistics leak = new ChannelStatistics(
                "leak", "L/min", 10.0, 0.0, 42.0, 30.0, 20.0,
                "", "22:10:00", ChannelStatistics.OFFSET_UNKNOWN, 600, 100);
        // LocalDateTime.toString() omits seconds when zero — 16 chars, not 19.
        DeviceEvent event = new DeviceEvent(
                "00:10:00", 630.0, 12.0, "2026-05-19T22:10", "Hypopnea", "H");

        List<ObjectNode> moments = OscarEventCorrelator.buildNotableMoments(
                Map.of("leak", leak),
                List.of(event),
                "2026-05-19T22:00:00",
                120,
                5,
                5);

        assertThat(moments).hasSize(1);
        assertThat(moments.get(0).path("nearby_events")).hasSize(1);
        assertThat(moments.get(0).path("nearby_events").get(0).path("delta_seconds").asLong()).isEqualTo(30L);
    }

    @Test
    void notableMoment_skipsChannelWithUnknownOffset() {
        ChannelStatistics summaryOnly = new ChannelStatistics(
                "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN,
                "", "", ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0);
        DeviceEvent event = new DeviceEvent(
                "00:10:30", 630.0, 12.0, "2026-05-19T22:10:30", "Hypopnea", "H");

        List<ObjectNode> moments = OscarEventCorrelator.buildNotableMoments(
                Map.of("leak", summaryOnly),
                List.of(event),
                "2026-05-19T22:00:00",
                120,
                5,
                5);

        assertThat(moments).isEmpty();
    }
}
