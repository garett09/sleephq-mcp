package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.service.NightService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class NightTools {

    private final NightService nightService;

    public NightTools(NightService nightService) {
        this.nightService = nightService;
    }

    @McpTool(name = "get-night-stats",
            description = "Get the full nightly summary for a machine_date: AHI breakdown, pressure stats, leak rate, flow limitation, respiratory rate, EPAP, machine settings, pulse rate, SpO2, sleep/movement summary, usage, large_leak flag. Single call returns BOTH CPAP and O2 Ring data inline (no separate calls needed). Cached for 6h.")
    public String getNightStats(
            @McpToolParam(description = "machine_date_id from list-machine-dates", required = true) String machineDateId) {
        return McpResponses.safe(() -> nightService.getNightStats(machineDateId));
    }

    @McpTool(name = "get-sessions",
            description = "Get mask on/off session boundaries for a specific night. Shows exact therapy start/stop times and mid-night mask removal events.")
    public String getSessions(
            @McpToolParam(description = "machine_date_id", required = true) String machineDateId) {
        return McpResponses.safe(() -> nightService.getSessions(machineDateId));
    }

    @McpTool(name = "get-events",
            description = "Get apnea/hypopnea event timestamps for a specific night. Use to correlate with waveform data for morphology analysis (obstructive vs central).")
    public String getEvents(
            @McpToolParam(description = "machine_date_id", required = true) String machineDateId) {
        return McpResponses.safe(() -> nightService.getEvents(machineDateId));
    }
}
