package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.service.DeviceContextService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class MachineTools {

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;
    private final DeviceContextService deviceContext;

    public MachineTools(SleepHqClient client, ClinicalContextProperties clinical,
                        DeviceContextService deviceContext) {
        this.client = client;
        this.clinical = clinical;
        this.deviceContext = deviceContext;
    }

    @McpTool(name = "list-teams",
            description = "List all SleepHQ teams the authenticated user belongs to. Returns JSON:API envelope with team id, name, time_zone, and machine relationships.")
    public String listTeams(
            @McpToolParam(description = "1-based page number (default 1)", required = false) Integer page,
            @McpToolParam(description = "Items per page (default server-side, typically 25)", required = false) Integer perPage) {
        return McpResponses.safe(() -> client.listTeams(page, perPage));
    }

    @McpTool(name = "list-machines",
            description = "List machines (CPAP, O2 Ring) for a team. teamId defaults to the configured SLEEPHQ_TEAM_ID if omitted.")
    public String listMachines(
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID env var.", required = false) String teamId,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(teamId, clinical.defaultTeamId());
        return McpResponses.safe(() -> client.listMachines(resolved, page, perPage));
    }

    @McpTool(name = "get-machine-details",
            description = "Get details (brand, model, serial) for a specific machine.")
    public String getMachineDetails(
            @McpToolParam(description = "Machine ID", required = true) String machineId) {
        return McpResponses.safe(() -> client.getMachine(machineId));
    }

    @McpTool(name = "list-machine-dates",
            description = "List per-night machine_date records for a machine. Returns IDs needed for get-night-stats. machineId defaults to SLEEPHQ_CPAP_MACHINE_ID if omitted.")
    public String listMachineDates(
            @McpToolParam(description = "Machine ID; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false) String machineId,
            @McpToolParam(description = "Sort order: 'asc' or 'desc' (desc = most recent first)", required = false) String sortOrder,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(machineId, clinical.defaultCpapMachineId());
        return McpResponses.safe(() -> client.listMachineDates(resolved, sortOrder, page, perPage));
    }

    @McpTool(name = "get-device-context",
            description = "Live device context from SleepHQ: latest machine_settings (pressure, mode, EPR, ramp, mask type), CPAP/O2 machine records, registered masks, configured env ids. Single source of truth — no repo edits for pressure changes.")
    public String getDeviceContext(
            @McpToolParam(description = "CPAP machine ID; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false)
            String machineId) {
        return McpResponses.safe(() -> deviceContext.deviceContextJson(machineId));
    }

    @McpTool(name = "get-latest-device-settings",
            description = "Alias for get-device-context (backward compatible).")
    public String getLatestDeviceSettings(
            @McpToolParam(description = "CPAP machine ID; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false)
            String machineId) {
        return getDeviceContext(machineId);
    }

    @McpTool(name = "get-machine-date-by-date",
            description = "Fetch the machine_date record for a specific calendar date (YYYY-MM-DD). machineId defaults to SLEEPHQ_CPAP_MACHINE_ID.")
    public String getMachineDateByDate(
            @McpToolParam(description = "Date in YYYY-MM-DD format", required = true) String date,
            @McpToolParam(description = "Machine ID; defaults to SLEEPHQ_CPAP_MACHINE_ID", required = false) String machineId) {
        String resolved = firstNonBlank(machineId, clinical.defaultCpapMachineId());
        return McpResponses.safe(() -> client.getMachineDateByDate(resolved, date));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        throw new IllegalArgumentException("Required ID missing and no default configured");
    }
}
