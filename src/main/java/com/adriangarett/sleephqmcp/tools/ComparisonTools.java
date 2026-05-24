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
            description = "Compare therapy metrics across a date range. Defaults machineId to SLEEPHQ_CPAP_MACHINE_ID if omitted.")
    public String getComparison(
            @McpToolParam(description = "Range start YYYY-MM-DD", required = true) String fromDate,
            @McpToolParam(description = "Range end YYYY-MM-DD", required = true) String toDate,
            @McpToolParam(description = "Machine ID; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false) String machineId) {
        String resolved = (machineId != null && !machineId.isBlank())
                ? machineId : clinical.defaultCpapMachineId();
        return McpResponses.safe(() -> service.compare(resolved, fromDate, toDate));
    }

    @McpTool(name = "get-share-dashboard",
            description = "Get the public share-link dashboard for a date. shareLinkToken defaults to SLEEPHQ_SHARE_LINK if omitted.")
    public String getShareDashboard(
            @McpToolParam(description = "Share link token; defaults to SLEEPHQ_SHARE_LINK", required = false) String shareLinkToken) {
        String resolved = (shareLinkToken != null && !shareLinkToken.isBlank())
                ? shareLinkToken : clinical.defaultShareLinkToken();
        return McpResponses.safe(() -> service.shareDashboard(resolved));
    }
}
