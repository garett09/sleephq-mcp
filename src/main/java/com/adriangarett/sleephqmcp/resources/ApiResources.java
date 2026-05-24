package com.adriangarett.sleephqmcp.resources;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.service.NightService;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Dynamic resources — URI templates that delegate to the service layer. The LLM can pin
 * a specific machine / team / night into its context as a resource and reference it by URI.
 */
@Component
public class ApiResources {

    private final SleepHqClient client;
    private final NightService nightService;

    public ApiResources(SleepHqClient client, NightService nightService) {
        this.client = client;
        this.nightService = nightService;
    }

    @McpResource(uri = "sleephq://team/{teamId}",
            name = "SleepHQ team",
            description = "Single team JSON:API document filtered from GET /api/v1/teams (no GET-by-id in SleepHQ).",
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
            description = "Full nightly summary by machine_date id (cached). All CPAP + O2 stats inline.",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult machineDate(String machineDateId) {
        return json("sleephq://machine_date/" + machineDateId, nightService.getNightStats(machineDateId));
    }

    private static McpSchema.ReadResourceResult json(String uri, String body) {
        return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(uri, "application/json", body)));
    }
}
