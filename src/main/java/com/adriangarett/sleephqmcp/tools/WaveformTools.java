package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.service.WaveformService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import com.adriangarett.sleephqmcp.support.TimeParams;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Waveform tools (one per channel). All logic lives in {@link WaveformService}.
 * Adding a new waveform channel = one enum constant in {@link WaveformChannel} + one
 * 2-line tool method here.
 *
 * <p>Contract: without {@code fromTime}/{@code toTime} returns summary statistics only.
 * With a window (HH:MM:SS each) returns RAW native-rate samples — never decimated.
 */
@Component
public class WaveformTools {

    private static final String WINDOW_NOTE =
            " Without fromTime+toTime, returns summary stats only. Supply both (HH:MM:SS) to receive raw native-rate samples for that window.";

    private final WaveformService service;

    public WaveformTools(WaveformService service) {
        this.service = service;
    }

    @McpTool(name = "get-flow-rate-data",
            description = "Get CPAP flow rate waveform (25 Hz). Use to inspect breathing morphology and distinguish obstructive from central apnea." + WINDOW_NOTE)
    public String getFlowRateData(
            @McpToolParam(description = "machine_date_id", required = true) String machineDateId,
            @McpToolParam(description = "Window start HH:MM:SS", required = false) String fromTime,
            @McpToolParam(description = "Window end HH:MM:SS", required = false) String toTime) {
        return fetch(WaveformChannel.FLOW_RATE, machineDateId, fromTime, toTime);
    }

    @McpTool(name = "get-pressure-data",
            description = "Get CPAP pressure waveform (25 Hz). Shows how AutoSet/fixed pressure varied through the night." + WINDOW_NOTE)
    public String getPressureData(
            @McpToolParam(description = "machine_date_id", required = true) String machineDateId,
            @McpToolParam(description = "Window start HH:MM:SS", required = false) String fromTime,
            @McpToolParam(description = "Window end HH:MM:SS", required = false) String toTime) {
        return fetch(WaveformChannel.PRESSURE, machineDateId, fromTime, toTime);
    }

    @McpTool(name = "get-leak-data",
            description = "Get leak rate waveform (25 Hz). Correlate spikes with session boundaries (mask seal) vs mid-session (mask shift). ResMed threshold: 24 L/min." + WINDOW_NOTE)
    public String getLeakData(
            @McpToolParam(description = "machine_date_id", required = true) String machineDateId,
            @McpToolParam(description = "Window start HH:MM:SS", required = false) String fromTime,
            @McpToolParam(description = "Window end HH:MM:SS", required = false) String toTime) {
        return fetch(WaveformChannel.LEAK, machineDateId, fromTime, toTime);
    }

    @McpTool(name = "get-spo2-data",
            description = "Get O2 Ring SpO2 waveform (1 Hz). Identify desat events below 90%." + WINDOW_NOTE)
    public String getSpo2Data(
            @McpToolParam(description = "machine_date_id for the O2 Ring", required = true) String machineDateId,
            @McpToolParam(description = "Window start HH:MM:SS", required = false) String fromTime,
            @McpToolParam(description = "Window end HH:MM:SS", required = false) String toTime) {
        return fetch(WaveformChannel.SPO2, machineDateId, fromTime, toTime);
    }

    @McpTool(name = "get-pulse-rate-data",
            description = "Get O2 Ring heart-rate waveform (1 Hz). Correlate with arousals." + WINDOW_NOTE)
    public String getPulseRateData(
            @McpToolParam(description = "machine_date_id for the O2 Ring", required = true) String machineDateId,
            @McpToolParam(description = "Window start HH:MM:SS", required = false) String fromTime,
            @McpToolParam(description = "Window end HH:MM:SS", required = false) String toTime) {
        return fetch(WaveformChannel.PULSE_RATE, machineDateId, fromTime, toTime);
    }

    @McpTool(name = "get-tidal-volume-data",
            description = "Get CPAP tidal volume waveform (25 Hz, mL). May 404 if SleepHQ has not finished processing that night."
                    + WINDOW_NOTE)
    public String getTidalVolumeData(
            @McpToolParam(description = "machine_date_id", required = true) String machineDateId,
            @McpToolParam(description = "Window start HH:MM:SS", required = false) String fromTime,
            @McpToolParam(description = "Window end HH:MM:SS", required = false) String toTime) {
        return fetch(WaveformChannel.TIDAL_VOLUME, machineDateId, fromTime, toTime);
    }

    private String fetch(WaveformChannel channel, String machineDateId, String fromTime, String toTime) {
        return McpResponses.safe(() -> service.fetch(channel, machineDateId,
                TimeParams.parseOrNull(fromTime),
                TimeParams.parseOrNull(toTime)).toJson());
    }
}
