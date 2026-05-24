package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.service.CombinedNightService;
import com.adriangarett.sleephqmcp.service.NightService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class NightTools {

    private final NightService nightService;
    private final CombinedNightService combinedNightService;

    public NightTools(NightService nightService, CombinedNightService combinedNightService) {
        this.nightService = nightService;
        this.combinedNightService = combinedNightService;
    }

    @McpTool(name = "get-night-stats",
            description = "GET /api/v1/machine_dates/{id} (SleepHQ Swagger: Machine Dates). Full nightly JSON: AHI, pressure, leak, flow limit, resp rate, EPAP, machine_settings, pulse_rate_summary, spo2_summary, movement_summary, usage, large_leak, etc. Summaries for SpO2/pulse/movement come from this single response—not from separate summary endpoints. Cached 6h.")
    public String getNightStats(
            @McpToolParam(description = "machine_date_id from list-machine-dates", required = true) String machineDateId) {
        return McpResponses.safe(() -> nightService.getNightStats(machineDateId));
    }

    @McpTool(name = "get-combined-night-by-date",
            description = "Same JSON:API envelope as get-night-stats: { data: { id, type: machine_date, attributes, relationships } }. Fetches CPAP and optionally O2 Ring nights for one calendar date (GET .../machines/{id}/machine_dates/{date}); copies spo2_summary, pulse_rate_summary, movement_summary from the O2 row when CPAP has them null/empty. data.id is the CPAP machine_date id. Requires date=YYYY-MM-DD and SLEEPHQ_CPAP_MACHINE_ID (or cpapMachineId). SLEEPHQ_O2_MACHINE_ID (or o2MachineId) optional—omit both for CPAP-only. Fails if CPAP has no row that night; O2 404 leaves oximetry fields unchanged.")
    public String getCombinedNightByDate(
            @McpToolParam(description = "Calendar night YYYY-MM-DD", required = true) String date,
            @McpToolParam(description = "CPAP machine id; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false) String cpapMachineId,
            @McpToolParam(description = "O2 Ring machine id; defaults to SLEEPHQ_O2_MACHINE_ID", required = false) String o2MachineId) {
        return McpResponses.safe(() -> combinedNightService.combineForCalendarDate(date, cpapMachineId, o2MachineId));
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
