package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.service.ComparisonService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ComparisonTools {

    private final ComparisonService service;
    private final ClinicalContextProperties clinical;

    public ComparisonTools(ComparisonService service, ClinicalContextProperties clinical) {
        this.service = service;
        this.clinical = clinical;
    }

    @McpTool(name = "get-comparison",
            description = "Multi-night therapy view without SleepHQ /comparisons. For each calendar day from fromDate to toDate (inclusive), fetches CPAP machine_date via GET .../machines/{id}/machine_dates/{date} and merges O2 summaries when SLEEPHQ_O2_MACHINE_ID is set (same logic as get-combined-night-by-date). Returns JSON: meta + nights[] (each { date, data } or { date, skipped, reason }). machineId defaults to SLEEPHQ_CPAP_MACHINE_ID. Max 120 days.")
    public String getComparison(
            @McpToolParam(description = "Range start YYYY-MM-DD", required = true) String fromDate,
            @McpToolParam(description = "Range end YYYY-MM-DD", required = true) String toDate,
            @McpToolParam(description = "Machine ID; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false) String machineId) {
        String resolved = (machineId != null && !machineId.isBlank())
                ? machineId : clinical.defaultCpapMachineId();
        return McpResponses.safe(() -> service.compare(resolved, fromDate, toDate));
    }
}
