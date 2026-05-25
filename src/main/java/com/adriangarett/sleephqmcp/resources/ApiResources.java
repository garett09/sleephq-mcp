package com.adriangarett.sleephqmcp.resources;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.service.ComparisonService;
import com.adriangarett.sleephqmcp.service.NightService;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Dynamic MCP resources — live SleepHQ JSON pinned by URI.
 */
@Component
public class ApiResources {

    private final SleepHqClient client;
    private final NightService nightService;
    private final ComparisonService comparisonService;
    private final ClinicalContextProperties clinical;

    public ApiResources(SleepHqClient client, NightService nightService,
                        ComparisonService comparisonService, ClinicalContextProperties clinical) {
        this.client = client;
        this.nightService = nightService;
        this.comparisonService = comparisonService;
        this.clinical = clinical;
    }

    @McpResource(uri = "sleephq://team/{teamId}",
            name = "SleepHQ team",
            description = "Single team JSON:API document from GET /api/v1/teams.",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult team(String teamId) {
        try {
            String id = SleepHqPathParams.requireResourceId(teamId, "teamId");
            String collection = client.listTeams(null, null);
            String single = JsonApi.toSingleResourceJsonFromCollection(collection, id);
            return json("sleephq://team/" + id, single);
        } catch (IllegalArgumentException e) {
            String label = teamId == null ? "unknown" : teamId;
            String body = JsonApi.toJsonString(Map.of(
                    "error", Map.of("kind", "validation", "message", e.getMessage())));
            return json("sleephq://team/" + label, body);
        }
    }

    @McpResource(uri = "sleephq://machine/{machineId}",
            name = "Machine details",
            description = "Live machine record (brand, model, serial) by id.",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult machine(String machineId) {
        return json("sleephq://machine/" + machineId, client.getMachine(machineId));
    }

    @McpResource(uri = "sleephq://machine_date/{machineDateId}",
            name = "Night summary",
            description = "Full machine_date JSON by id (CPAP + O2 summaries when merged upstream).",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult machineDate(String machineDateId) {
        return json("sleephq://machine_date/" + machineDateId, nightService.getNightStats(machineDateId));
    }

    @McpResource(uri = "sleephq://comparison/{fromDate}/{toDate}",
            name = "Multi-night comparison",
            description = "Per-night merged machine_date + journal for date range (max 120 days). Defaults to SLEEPHQ_CPAP_MACHINE_ID.",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult comparison(String fromDate, String toDate) {
        String cpap = clinical.defaultCpapMachineId();
        if (cpap == null || cpap.isBlank()) {
            throw new IllegalArgumentException("SLEEPHQ_CPAP_MACHINE_ID is not configured");
        }
        String body = comparisonService.compare(cpap,
                SleepHqPathParams.requireCalendarDate(fromDate, "fromDate"),
                SleepHqPathParams.requireCalendarDate(toDate, "toDate"));
        return json("sleephq://comparison/" + fromDate + "/" + toDate, body);
    }

    private static McpSchema.ReadResourceResult json(String uri, String body) {
        return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(uri, "application/json", body)));
    }
}
