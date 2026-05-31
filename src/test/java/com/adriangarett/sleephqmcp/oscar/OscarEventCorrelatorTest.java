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
                "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, 20.0,
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
                "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, 20.0,
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
    void sort_prefersHighDeviationOverHighRawValue() {
        // spo2 min=85 (avg=97) → deviation 12; higher raw value than leak
        // leak max=42 (avg=10) → deviation 32; should rank first
        ChannelStatistics spo2 = new ChannelStatistics(
                "spo2", "%", 97.0, 85.0, 98.0, 96.0, Double.NaN, Double.NaN,
                "22:10:00", "", 600, ChannelStatistics.OFFSET_UNKNOWN, 100);
        ChannelStatistics leak = new ChannelStatistics(
                "leak", "L/min", 10.0, 2.0, 42.0, 30.0, Double.NaN, Double.NaN,
                "", "22:15:00", ChannelStatistics.OFFSET_UNKNOWN, 900, 100);
        DeviceEvent event = new DeviceEvent(
                "00:10:30", 630.0, 12.0, "2026-05-19T22:10:30", "Hypopnea", "H");

        List<ObjectNode> moments = OscarEventCorrelator.buildNotableMoments(
                Map.of("spo2", spo2, "leak", leak),
                List.of(event),
                "2026-05-19T22:00:00",
                300, 5, 5);

        assertThat(moments).hasSizeGreaterThanOrEqualTo(2);
        assertThat(moments.get(0).path("channel").asText()).isEqualTo("leak");
    }

    @Test
    void nearbyEvents_selectsClosestNotEarliestWhenCapReached() {
        // Channel max at 500s; events at 100s (delta=400), 490s (delta=10), 495s (delta=5), 510s (delta=10)
        // maxNearbyEvents=2 — should pick the 2 closest, not the 2 earliest
        ChannelStatistics leak = new ChannelStatistics(
                "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, Double.NaN,
                "", "22:08:20", ChannelStatistics.OFFSET_UNKNOWN, 500, 100);
        List<DeviceEvent> events = List.of(
                new DeviceEvent("00:01:40", 100.0, 5.0, "2026-05-19T22:01:40", "Hypopnea", "H"),
                new DeviceEvent("00:08:10", 490.0, 5.0, "2026-05-19T22:08:10", "Obstructive", "O"),
                new DeviceEvent("00:08:15", 495.0, 5.0, "2026-05-19T22:08:15", "RERA", "R"),
                new DeviceEvent("00:08:30", 510.0, 5.0, "2026-05-19T22:08:30", "Hypopnea", "H")
        );

        List<ObjectNode> moments = OscarEventCorrelator.buildNotableMoments(
                Map.of("leak", leak),
                events,
                "2026-05-19T22:00:00",
                450, 5, 2);

        assertThat(moments).hasSize(1);
        assertThat(moments.get(0).path("nearby_events")).hasSize(2);
        assertThat(moments.get(0).path("nearby_events").get(0).path("delta_seconds").asLong()).isEqualTo(5L);
        assertThat(moments.get(0).path("nearby_events").get(0).path("label").asText()).isEqualTo("RERA");
    }

    @Test
    void notableMoment_skipsChannelWithUnknownOffset() {
        ChannelStatistics summaryOnly = new ChannelStatistics(
                "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, Double.NaN,
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
