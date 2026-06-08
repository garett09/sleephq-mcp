package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.service.OscarEventsService;
import com.adriangarett.sleephqmcp.service.OscarMechanicsService;
import com.adriangarett.sleephqmcp.service.OscarPlmdService;
import com.adriangarett.sleephqmcp.service.OscarTrendService;
import com.adriangarett.sleephqmcp.service.UnifiedNightAnalysisService;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.McpResponses;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class OscarTools {

    private final OscarRepository oscarRepository;
    private final UnifiedNightAnalysisService nightAnalysisService;
    private final OscarMechanicsService mechanicsService;
    private final OscarTrendService trendService;
    private final OscarEventsService eventsService;
    private final OscarPlmdService plmdService;

    public OscarTools(
            OscarRepository oscarRepository,
            UnifiedNightAnalysisService nightAnalysisService,
            OscarMechanicsService mechanicsService,
            OscarTrendService trendService,
            OscarEventsService eventsService,
            OscarPlmdService plmdService) {
        this.oscarRepository = oscarRepository;
        this.nightAnalysisService = nightAnalysisService;
        this.mechanicsService = mechanicsService;
        this.trendService = trendService;
        this.eventsService = eventsService;
        this.plmdService = plmdService;
    }

    @McpTool(name = "get-oscar-status",
            description = "Reports whether local OSCAR data is configured, device folder, last session date.")
    public String getOscarStatus() {
        return McpResponses.safe(this::serializeOscarStatus);
    }

    @McpTool(name = "get-night-analysis",
            description = "Server-side OSCAR night analysis: channel stats, events, notable_moments. Compact JSON.")
    public String getNightAnalysis(
            @McpToolParam(description = "Calendar night YYYY-MM-DD", required = true) String date) {
        return McpResponses.safe(() -> serializeNightAnalysis(date));
    }

    @McpTool(name = "get-mechanics",
            description = "OSCAR mechanics slice (channels + respiratory_indices) for one date. Prefer get-combined-night-by-date.")
    public String getMechanics(
            @McpToolParam(description = "Calendar night YYYY-MM-DD", required = true) String date) {
        return McpResponses.safe(() -> mechanicsService.mechanicsForDate(date));
    }

    @McpTool(name = "get-oscar-trend",
            description = "Pre-aggregated nightly night_analysis rows for a date range (no raw waveforms). "
                    + "detail=summary (default) returns slim rows; detail=full preserves the complete shape.")
    public String getOscarTrend(
            @McpToolParam(description = "Number of nights ending at last session (1-90)", required = true) int days,
            @McpToolParam(description = "Optional end date YYYY-MM-DD", required = false) String endDate,
            @McpToolParam(description = "summary (default) or full", required = false) String detail) {
        return McpResponses.safe(() -> endDate == null || endDate.isBlank()
                ? trendService.trend(days, detail)
                : trendService.trend(endDate, days, detail));
    }

    @McpTool(name = "get-oscar-events",
            description = "OSCAR EVE.edf events. Default detail=summary (counts + timed_sample cap). detail=full for all events.")
    public String getOscarEvents(
            @McpToolParam(description = "Calendar night YYYY-MM-DD", required = true) String date,
            @McpToolParam(description = "summary (default) or full", required = false) String detail) {
        return McpResponses.safe(() -> eventsService.eventsForDate(date, detail));
    }

    @McpTool(name = "get-plmd-night",
            description = "PLMD-style deltas: movement_summary vs PLD Vt/RR means (best-effort, no raw waveforms).")
    public String getPlmdNight(
            @McpToolParam(description = "Calendar night YYYY-MM-DD", required = true) String date,
            @McpToolParam(description = "O2 machine id; defaults to SLEEPHQ_O2_MACHINE_ID", required = false) String o2MachineId) {
        return McpResponses.safe(() -> plmdService.plmdNight(date, o2MachineId));
    }

    private String serializeOscarStatus() {
        try {
            ObjectNode root = JsonApi.mapper().createObjectNode();
            root.put("configured", oscarRepository.isConfigured());
            root.put("reachable", oscarRepository.isReachable());
            oscarRepository.dbFile().ifPresentOrElse(
                    path -> root.put("db_file", path.toString()),
                    () -> root.putNull("db_file"));
            oscarRepository.getLastSessionDate()
                    .ifPresent(d -> root.put("last_session_date", d.toString()));
            root.put("oscar_status", oscarRepository.isReachable() ? "ok" : "unavailable");
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize oscar status", e);
        }
    }

    private String serializeNightAnalysis(String date) {
        SleepHqPathParams.requireCalendarDate(date, "date");
        return nightAnalysisService.analyzeNight(date)
                .map(node -> {
                    try {
                        return JsonApi.mapper().writeValueAsString(node);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to serialize night_analysis", e);
                    }
                })
                .orElseGet(() -> JsonApi.toJsonString(java.util.Map.of(
                        "date", date,
                        "oscar_status", "unavailable")));
    }
}
