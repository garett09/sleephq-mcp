package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResponse;
import com.adriangarett.sleephqmcp.domain.WaveformStats;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationWaveformServiceTest {

    @Mock
    private WaveformService waveformService;

    @InjectMocks
    private CorrelationWaveformService correlationWaveformService;

    @Test
    void fetchWindow_mergesChannelJson() {
        WaveformStats stats = WaveformStats.from(new double[]{1, 2, 3}, 25.0);
        when(waveformService.fetch(eq(WaveformChannel.FLOW_RATE), eq("md-1"),
                eq(LocalTime.of(1, 0, 0)), eq(LocalTime.of(1, 0, 1))))
                .thenReturn(new WaveformResponse.Stats(WaveformChannel.FLOW_RATE, stats));
        when(waveformService.fetch(eq(WaveformChannel.PRESSURE), eq("md-1"),
                eq(LocalTime.of(1, 0, 0)), eq(LocalTime.of(1, 0, 1))))
                .thenReturn(new WaveformResponse.Stats(WaveformChannel.PRESSURE, stats));

        String json = correlationWaveformService.fetchWindow(
                "md-1",
                LocalTime.of(1, 0, 0),
                LocalTime.of(1, 0, 1),
                List.of(WaveformChannel.FLOW_RATE, WaveformChannel.PRESSURE));

        JsonNode root = JsonApi.parse(json);
        assertThat(root.path("machine_date_id").asText()).isEqualTo("md-1");
        assertThat(root.path("channels").path("flow_rate").path("mode").asText()).isEqualTo("stats");
        assertThat(root.path("channels").path("pressure").path("mode").asText()).isEqualTo("stats");
    }
}
