package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.service.JournalLookupService;
import com.adriangarett.sleephqmcp.support.JournalOverlaySupport;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.McpResponses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TeamDataTools {

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;
    private final JournalLookupService journalLookup;

    public TeamDataTools(SleepHqClient client, ClinicalContextProperties clinical,
                         JournalLookupService journalLookup) {
        this.client = client;
        this.clinical = clinical;
        this.journalLookup = journalLookup;
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

    @McpTool(name = "list-patients",
            description = "List patients for a SleepHQ Consult (clinic) team. teamId defaults to SLEEPHQ_TEAM_ID.")
    public String listPatients(
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID.", required = false) String teamId,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(teamId, clinical.defaultTeamId());
        return McpResponses.safe(() -> client.listPatients(resolved, page, perPage));
    }

    @McpTool(name = "list-imports",
            description = "List data imports for a team. Each import groups one or more uploaded device files. teamId defaults to SLEEPHQ_TEAM_ID.")
    public String listImports(
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID.", required = false) String teamId,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(teamId, clinical.defaultTeamId());
        return McpResponses.safe(() -> client.listImports(resolved, page, perPage));
    }

    @McpTool(name = "get-import",
            description = "Retrieve a single import record by ID, including status, progress, machine_id, and linked file IDs.")
    public String getImport(
            @McpToolParam(description = "Import ID from list-imports", required = true) String importId) {
        return McpResponses.safe(() -> client.getImport(importId));
    }

    @McpTool(name = "list-files",
            description = "List uploaded import files for a team. Each record includes name, path, size, and download_url (signed, expires 5 min). " +
                    "nameFilter does a case-insensitive substring match on filename and auto-pages up to 500 files. " +
                    "Without nameFilter, returns one page of results. teamId defaults to SLEEPHQ_TEAM_ID.")
    public String listTeamFiles(
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID.", required = false) String teamId,
            @McpToolParam(description = "Substring to match against filename, e.g. \"BRP.edf\" or \".edf\"", required = false) String nameFilter,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        String resolved = firstNonBlank(teamId, clinical.defaultTeamId());
        if (nameFilter != null && !nameFilter.isBlank()) {
            return McpResponses.safe(() -> listFilesFiltered(resolved, nameFilter.trim()));
        }
        return McpResponses.safe(() -> client.listTeamFiles(resolved, page, perPage));
    }

    private String listFilesFiltered(String teamId, String nameFilter) {
        String lower = nameFilter.toLowerCase(Locale.ROOT);
        ArrayNode matches = JsonApi.mapper().createArrayNode();
        boolean truncated = false;
        int pageNum = 1;
        while (pageNum <= 5) {  // max 500 files (5 pages × 100)
            JsonNode root = JsonApi.parse(client.listTeamFiles(teamId, pageNum, 100));
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) break;
            for (JsonNode item : data) {
                String name = item.path("attributes").path("name").asText("");
                if (name.toLowerCase(Locale.ROOT).contains(lower)) {
                    matches.add(item);
                }
            }
            if (data.size() < 100) break;  // last page
            if (pageNum == 5) {
                truncated = true;
                break;
            }
            pageNum++;
        }
        ObjectNode result = JsonApi.mapper().createObjectNode();
        result.set("data", matches);
        result.put("meta_total_matches", matches.size());
        result.put("search_truncated", truncated);
        if (truncated) {
            result.put("search_truncated_reason",
                    "Stopped at 500-file limit (5 pages × 100); matches shown are partial — use a narrower nameFilter to reduce scope.");
        }
        try {
            return JsonApi.mapper().writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize filtered files", e);
        }
    }

    @McpTool(name = "list-import-files",
            description = "List files attached to a specific import. Returns file metadata including name, path, size, and download_url (signed, expires 5 min).")
    public String listImportFiles(
            @McpToolParam(description = "Import ID from list-imports or get-import", required = true) String importId,
            @McpToolParam(required = false) Integer page,
            @McpToolParam(required = false) Integer perPage) {
        return McpResponses.safe(() -> client.listImportFiles(importId, page, perPage));
    }

    @McpTool(name = "get-import-file",
            description = "Retrieve metadata for a single import file, including a signed download_url valid for 5 minutes. Use the URL to fetch the raw device file (e.g. STR.edf, BRP.edf).")
    public String getImportFile(
            @McpToolParam(description = "File ID from list-files or list-import-files", required = true) String fileId) {
        return McpResponses.safe(() -> client.getImportFile(fileId));
    }

    @McpTool(name = "get-journal",
            description = "Retrieve a single journal entry by ID. Returns JSON:API data plus top-level journal wellness (sleep_stages_summary with merged timeline, sleep_stages_parsed, step_count, active_energy_joules when present).")
    public String getJournal(
            @McpToolParam(description = "Journal ID from list-journals", required = true) String journalId) {
        return McpResponses.safe(() -> {
            String raw = client.getJournal(journalId);
            ObjectNode envelope = (ObjectNode) JsonApi.parse(raw);
            JsonNode attrs = envelope.path("data").path("attributes");
            JournalOverlaySupport.attachIfPresent(envelope, attrs);
            try {
                return JsonApi.mapper().writeValueAsString(envelope);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize journal", e);
            }
        });
    }

    @McpTool(name = "get-journal-by-date",
            description = "Find the team journal row for a calendar date (YYYY-MM-DD). Returns { journal: { step_count, sleep_stages_summary?, sleep_stages_parsed?, active_energy_joules?, feeling_score, feeling_label (Awful..Great), weight_grams, notes } } or { journal: null }. sleep_stages_summary includes stage_type_legend (Apple Health: 2=awake, 3=core, 4=deep, 5=rem) and minutes_by_stage. teamId defaults to SLEEPHQ_TEAM_ID.")
    public String getJournalByDate(
            @McpToolParam(description = "Calendar date YYYY-MM-DD", required = true) String date,
            @McpToolParam(description = "Team ID from list-teams. Defaults to SLEEPHQ_TEAM_ID.", required = false) String teamId) {
        return McpResponses.safe(() -> {
            String resolvedTeam = firstNonBlank(teamId, clinical.defaultTeamId());
            var attrs = journalLookup.findAttributesByDate(resolvedTeam, date);
            ObjectNode result = JsonApi.mapper().createObjectNode();
            if (attrs.isPresent()) {
                ObjectNode wellness = JournalOverlaySupport.buildWellnessObject(attrs.get());
                if (wellness != null) {
                    result.set("journal", wellness);
                } else {
                    result.putNull("journal");
                }
            } else {
                result.putNull("journal");
            }
            try {
                return JsonApi.mapper().writeValueAsString(result);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize journal-by-date", e);
            }
        });
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
