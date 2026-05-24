package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.domain.WaveformSample;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaveformServiceTest {

    @Test
    void extractSamples_flatNumericArray() {
        JsonNode node = JsonApi.parse("{\"data\":[1.0, 2.0, 3.0, 4.0]}");
        List<WaveformSample> samples = WaveformService.extractSamples(node, 25.0);
        assertThat(samples).hasSize(4);
        assertThat(samples.get(0).v()).isEqualTo(1.0);
        assertThat(samples.get(0).t()).isEqualTo(0.0);
        assertThat(samples.get(1).t()).isCloseTo(0.04, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void extractSamples_arrayOfTVPairs() {
        JsonNode node = JsonApi.parse("{\"data\":[{\"t\":0,\"v\":5.0},{\"t\":1,\"v\":6.0}]}");
        List<WaveformSample> samples = WaveformService.extractSamples(node, 1.0);
        assertThat(samples).hasSize(2);
        assertThat(samples.get(1).v()).isEqualTo(6.0);
        assertThat(samples.get(1).t()).isEqualTo(1.0);
    }

    @Test
    void extractSamples_jsonApiEnvelopeWithValues() {
        JsonNode node = JsonApi.parse("{\"data\":{\"attributes\":{\"values\":[10,20,30],\"interval_seconds\":0.04}}}");
        List<WaveformSample> samples = WaveformService.extractSamples(node, 25.0);
        assertThat(samples).hasSize(3);
        assertThat(samples.get(2).v()).isEqualTo(30.0);
        assertThat(samples.get(2).t()).isCloseTo(0.08, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void extractSamples_unknownShapeReturnsNull() {
        JsonNode node = JsonApi.parse("{\"unexpected\":\"shape\"}");
        assertThat(WaveformService.extractSamples(node, 25.0)).isNull();
    }

    @Test
    void sliceByWindow_inclusiveOnBothEnds() {
        List<WaveformSample> samples = List.of(
                new WaveformSample(0, 1),
                new WaveformSample(3600, 2),     // 01:00:00
                new WaveformSample(7200, 3),     // 02:00:00
                new WaveformSample(10800, 4));   // 03:00:00
        List<WaveformSample> sliced = WaveformService.sliceByWindow(
                samples, LocalTime.of(1, 0, 0), LocalTime.of(2, 0, 0));
        assertThat(sliced).hasSize(2);
        assertThat(sliced.get(0).v()).isEqualTo(2);
        assertThat(sliced.get(1).v()).isEqualTo(3);
    }
}
