package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.service.CorrelationWaveformService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import com.adriangarett.sleephqmcp.support.TimeParams;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * Multi-channel waveform fetch for a single time window.
 */
@Component
public class CorrelationTools {

    private static final String CHANNELS_HELP =
            "Comma-separated channels (enum names or *_data segments), max 6 distinct after dedupe, "
                    + "e.g. flow_rate,pressure,leak or spo2,pulse_rate. Default if omitted: flow_rate,pressure,leak. "
                    + "Each selected channel performs one full-night waveform HTTP fetch (same as single-channel tools), "
                    + "then the server slices to your window — N distinct channels ≈ N SleepHQ GETs.";

    private final CorrelationWaveformService correlationWaveformService;

    public CorrelationTools(CorrelationWaveformService correlationWaveformService) {
        this.correlationWaveformService = correlationWaveformService;
    }

    @McpTool(name = "get-correlation-window",
            description = "Fetch raw waveform samples for multiple channels over the same HH:MM:SS window in one JSON response. "
                    + "One waveform GET per distinct channel for that night (up to 6). "
                    + CHANNELS_HELP)
    public String getCorrelationWindow(
            @McpToolParam(description = "machine_date_id", required = true) String machineDateId,
            @McpToolParam(description = "Window start HH:MM:SS (required)", required = true) String fromTime,
            @McpToolParam(description = "Window end HH:MM:SS (required)", required = true) String toTime,
            @McpToolParam(description = CHANNELS_HELP, required = false) String channels) {
        return McpResponses.safe(() -> {
            LocalTime from = TimeParams.requireTime(fromTime, "fromTime");
            LocalTime to = TimeParams.requireTime(toTime, "toTime");
            List<WaveformChannel> list = resolveChannels(channels);
            return correlationWaveformService.fetchWindow(machineDateId, from, to, list);
        });
    }

    private static List<WaveformChannel> resolveChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            return List.of(WaveformChannel.FLOW_RATE, WaveformChannel.PRESSURE, WaveformChannel.LEAK);
        }
        return WaveformChannel.parseChannelList(channels);
    }
}
