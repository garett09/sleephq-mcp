package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.service.CombinedNightService;
import com.adriangarett.sleephqmcp.service.NightService;
import com.adriangarett.sleephqmcp.service.SleepHqNightSummaryService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class NightTools {

    private final NightService nightService;
    private final CombinedNightService combinedNightService;
    private final SleepHqNightSummaryService nightSummaryService;

    public NightTools(NightService nightService,
                      CombinedNightService combinedNightService,
                      SleepHqNightSummaryService nightSummaryService) {
        this.nightService = nightService;
        this.combinedNightService = combinedNightService;
        this.nightSummaryService = nightSummaryService;
    }

    @McpTool(name = "get-night-stats",
            description = "GET /api/v1/machine_dates/{id} (SleepHQ Swagger: Machine Dates). Returns { data: machine_date, journal?: wellness }. Journal includes sleep_stages_summary (minutes_by_stage, stage_type_legend), step_count, active_energy_joules when present. CPAP summaries: AHI, pressure, leak, spo2_summary, etc. Cached 6h for machine_date only.")
    public String getNightStats(
            @McpToolParam(description = "machine_date_id from list-machine-dates", required = true) String machineDateId) {
        return McpResponses.safe(() -> nightService.getNightStats(machineDateId));
    }

    @McpTool(name = "get-combined-night-by-date",
            description = "Same shape as get-night-stats: { data?, journal?, coverage, ahi_components?, therapy_display? }. therapy_display.apnea_indices_cell = OSA · CSA · H · AHI for user summaries. Prefers CPAP; without Magic Uploader may return O2 and/or journal only. Fails only when all three absent.")
    public String getCombinedNightByDate(
            @McpToolParam(description = "Calendar night YYYY-MM-DD", required = true) String date,
            @McpToolParam(description = "CPAP machine id; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false) String cpapMachineId,
            @McpToolParam(description = "O2 Ring machine id; defaults to SLEEPHQ_O2_MACHINE_ID", required = false) String o2MachineId) {
        return McpResponses.safe(() -> combinedNightService.combineForCalendarDate(date, cpapMachineId, o2MachineId));
    }

    @McpTool(name = "get-sleephq-night",
            description = "SleepHQ-only per-night channel summary (no OSCAR). Reads the local SleepHQ mirror first (RESMED_DATA/SLEEPHQ_O2_RING), falling back to the SleepHQ API. Aggregates ALL of a night's PLD sessions + O2-ring sessions and returns p99/p95/median + min/max/avg/count per channel: CPAP pressure, epap, mask_pressure, leak_rate, resp_rate, tidal_volume, minute_vent, snore, flow_limit; O2 spo2, pulse_rate, movement. Includes clinical markers (SpO2 T88/nadir, leak>24 L/min, pressure-at-max), self-validation vs machine_date summaries, and provenance (per-session, source=local|sleephq_api). Sleep stage and AHI come from get-combined-night-by-date, not here.")
    public String getSleephqNight(
            @McpToolParam(description = "Calendar night YYYY-MM-DD (CPAP grouped by ResMed DATALOG folder)", required = true) String date,
            @McpToolParam(description = "CPAP machine id; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false) String cpapMachineId,
            @McpToolParam(description = "O2 Ring machine id; defaults to SLEEPHQ_O2_MACHINE_ID", required = false) String o2MachineId) {
        return McpResponses.safe(() -> nightSummaryService.getNightSummary(date, cpapMachineId, o2MachineId));
    }

}
