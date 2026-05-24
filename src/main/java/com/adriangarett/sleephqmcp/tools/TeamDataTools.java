package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.McpResponses;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TeamDataTools {

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;

    public TeamDataTools(SleepHqClient client, ClinicalContextProperties clinical) {
        this.client = client;
        this.clinical = clinical;
    }

    @McpTool(name = "list-sleep-tests",
            description = "List sleep tests (PSG/HST style records) for a team. Optional bucket filter. teamId defaults to SLEEPHQ_TEAM_ID.")
    public String listSleepTests(
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID.", required = false) String teamId,
            @McpToolParam(description = "Optional bucket query token (SleepHQ-specific)", required = false) String bucket,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(teamId, clinical.defaultTeamId());
        return McpResponses.safe(() -> client.listSleepTests(resolved, bucket, page, perPage));
    }

    @McpTool(name = "list-journals",
            description = "List patient journal entries for a team. teamId defaults to SLEEPHQ_TEAM_ID.")
    public String listJournals(
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID.", required = false) String teamId,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(teamId, clinical.defaultTeamId());
        return McpResponses.safe(() -> client.listJournals(resolved, page, perPage));
    }

    @McpTool(name = "list-masks",
            description = "List masks (interfaces) for a team. teamId defaults to SLEEPHQ_TEAM_ID.")
    public String listMasks(
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID.", required = false) String teamId,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(teamId, clinical.defaultTeamId());
        return McpResponses.safe(() -> client.listMasks(resolved, page, perPage));
    }

    @McpTool(name = "list-devices",
            description = "List devices registered for the authenticated user (CPAP, O2 ring, etc.).")
    public String listDevices() {
        return McpResponses.safe(client::listDevices);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("Required teamId missing and no SLEEPHQ_TEAM_ID configured");
    }
}
