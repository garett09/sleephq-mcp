package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.service.CorrelationWaveformService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationToolsTest {

    @Mock
    private CorrelationWaveformService correlationWaveformService;

    @InjectMocks
    private CorrelationTools correlationTools;

    @Test
    void getCorrelationWindow_invalidFromTime_returnsValidationJson() {
        String body = correlationTools.getCorrelationWindow("md-1", "not-a-time", "01:02:03", null);

        JsonNode root = JsonApi.parse(body);
        assertThat(root.path("details").path("kind").asText()).isEqualTo("validation");
        assertThat(root.path("error").asText()).contains("fromTime");
    }

    @Test
    void getCorrelationWindow_blankChannels_usesDefaultThreeChannels() {
        when(correlationWaveformService.fetchWindow(
                eq("md-1"),
                eq(LocalTime.of(1, 0, 0)),
                eq(LocalTime.of(1, 0, 1)),
                eq(List.of(
                        WaveformChannel.FLOW_RATE,
                        WaveformChannel.PRESSURE,
                        WaveformChannel.LEAK))))
                .thenReturn("{}");

        String body = correlationTools.getCorrelationWindow("md-1", "01:00:00", "01:00:01", null);

        assertThat(body).isEqualTo("{}");
        verify(correlationWaveformService).fetchWindow(
                eq("md-1"),
                eq(LocalTime.of(1, 0, 0)),
                eq(LocalTime.of(1, 0, 1)),
                eq(List.of(WaveformChannel.FLOW_RATE, WaveformChannel.PRESSURE, WaveformChannel.LEAK)));
    }

    @Test
    void getCorrelationWindow_duplicateChannelTokens_dedupedInList() {
        when(correlationWaveformService.fetchWindow(
                eq("md-1"),
                eq(LocalTime.of(2, 0, 0)),
                eq(LocalTime.of(2, 0, 5)),
                eq(List.of(WaveformChannel.FLOW_RATE, WaveformChannel.PRESSURE))))
                .thenReturn("{}");

        correlationTools.getCorrelationWindow("md-1", "02:00:00", "02:00:05", "flow_rate,flow_rate,pressure");

        verify(correlationWaveformService).fetchWindow(
                eq("md-1"),
                eq(LocalTime.of(2, 0, 0)),
                eq(LocalTime.of(2, 0, 5)),
                eq(List.of(WaveformChannel.FLOW_RATE, WaveformChannel.PRESSURE)));
    }
}
