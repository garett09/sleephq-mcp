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

}
