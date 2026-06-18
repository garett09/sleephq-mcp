package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NightAnalysisSupportChannelNodeTest {

    @Test
    void channelStatsNode_emitsP99_5_whenPresent() {
        ChannelStatistics stat = new ChannelStatistics(
                "pressure", "cmH2O", 10.0, 7.0, 14.0, 13.0, 13.5, 9.5,
                "02:00:00", "06:00:00", 7200, 21600, 3600);

        ObjectNode node = NightAnalysisSupport.channelStatsNode(Map.of("pressure", stat));
        ObjectNode ch = (ObjectNode) node.path("pressure");

        assertThat(ch.has("p95")).isTrue();
        assertThat(ch.path("p95").asDouble()).isEqualTo(13.0);
        assertThat(ch.has("p99_5")).isTrue();
        assertThat(ch.path("p99_5").asDouble()).isEqualTo(13.5);
        assertThat(ch.has("median")).isTrue();
    }

    @Test
    void channelStatsNode_omitsP99_5_whenNaN() {
        ChannelStatistics stat = new ChannelStatistics(
                "pressure", "cmH2O", 10.0, 7.0, 14.0, 13.0, Double.NaN, 9.5,
                null, null, ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0);

        ObjectNode node = NightAnalysisSupport.channelStatsNode(Map.of("pressure", stat));
        ObjectNode ch = (ObjectNode) node.path("pressure");

        assertThat(ch.has("p99_5")).isFalse();
    }

    @Test
    void channelStatsNode_omitsP95_whenNaN() {
        ChannelStatistics stat = new ChannelStatistics(
                "pressure", "cmH2O", 10.0, 7.0, 14.0, Double.NaN, Double.NaN, 9.5,
                null, null, ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0);

        ObjectNode node = NightAnalysisSupport.channelStatsNode(Map.of("pressure", stat));
        ObjectNode ch = (ObjectNode) node.path("pressure");

        assertThat(ch.has("p95")).isFalse();
        assertThat(ch.path("avg").asDouble()).isEqualTo(10.0);
    }

    @Test
    void channelStatsNode_omitsAvgMinMax_whenNaN() {
        ChannelStatistics stat = new ChannelStatistics(
                "tidal_volume", "mL", Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                null, null, ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0);

        ObjectNode node = NightAnalysisSupport.channelStatsNode(Map.of("tidal_volume", stat));
        ObjectNode ch = (ObjectNode) node.path("tidal_volume");

        assertThat(ch.has("avg")).isFalse();
        assertThat(ch.has("min")).isFalse();
        assertThat(ch.has("max")).isFalse();
        assertThat(ch.has("p95")).isFalse();
    }
}
