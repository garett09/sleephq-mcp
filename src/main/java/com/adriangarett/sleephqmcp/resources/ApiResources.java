package com.adriangarett.sleephqmcp.resources;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.service.NightService;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;

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
            description = "Live team record by id.",
            mimeType = "application/json")
    public McpSchema.ReadResourceResult team(String teamId) {
        // Teams list endpoint is paginated; for a single team, fall through to listing for now.
        // SleepHQ has no single-team GET — list-teams returns all of them.
        return json("sleephq://team/" + teamId, client.listTeams(null, null));
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
