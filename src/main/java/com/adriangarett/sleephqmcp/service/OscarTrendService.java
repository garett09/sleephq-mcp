package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.adriangarett.sleephqmcp.support.VentilationSummarySupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OscarTrendService {

    private final OscarRepository oscarRepository;
    private final UnifiedNightAnalysisService nightAnalysisService;
    private final MachineDateAttributesLoader machineDateLoader;

    public OscarTrendService(OscarRepository oscarRepository,
                             UnifiedNightAnalysisService nightAnalysisService,
                             MachineDateAttributesLoader machineDateLoader) {
        this.oscarRepository = oscarRepository;
        this.nightAnalysisService = nightAnalysisService;
        this.machineDateLoader = machineDateLoader;
    }

    // ── public API ───────────────────────────────────────────────────────────

    public String trend(int days) {
        return trend(days, "summary");
    }

    public String trend(int days, String detail) {
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("days must be between 1 and 90");
        }
        LocalDate end = oscarRepository.getLastSessionDate().orElse(LocalDate.now());
        LocalDate start = end.minusDays(days - 1L);
        return serializeTrend(start, end, mode(detail));
    }

    public String trend(String endDate, int days) {
        return trend(endDate, days, "summary");
    }

    public String trend(String endDate, int days, String detail) {
        LocalDate end = LocalDate.parse(SleepHqPathParams.requireCalendarDate(endDate, "endDate"));
        LocalDate start = end.minusDays(days - 1L);
        return serializeTrend(start, end, mode(detail));
    }

    // ── internals ────────────────────────────────────────────────────────────

    /** Normalises the caller-supplied detail string to either {@code "summary"} or {@code "full"}. */
    private static String mode(String detail) {
        if (detail == null || detail.isBlank()) {
            return "summary";
        }
        String lower = detail.strip().toLowerCase();
        return "full".equals(lower) ? "full" : "summary";
    }

    /**
     * One row per OSCAR session in the calendar window ({@code start}..{@code end} inclusive).
     * Walks each calendar day; when a session spans multiple days, the latest in-range date wins
     * (matches {@code get-combined-night-by-date} end-night queries).
     */
    private String serializeTrend(LocalDate start, LocalDate end, String mode) {
        ObjectNode root = JsonApi.mapper().createObjectNode();
        root.put("start_date", start.toString());
        root.put("end_date", end.toString());
        root.put("detail", mode);
        ArrayNode nights = root.putArray("nights");
        Map<String, ObjectNode> bySessionId = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            JsonNode machineDateAttrs = machineDateLoader.loadOrNull(date.toString());
            nightAnalysisService.analyzeNight(date.toString(), machineDateAttrs, null).ifPresent(node -> {
                String sessionId = node.path("session").path("session_id").asText("");
                if (!sessionId.isBlank()) {
                    bySessionId.put(sessionId, node);
                }
            });
        }
        ObjectNode ventilation = VentilationSummarySupport.fromOscarChannels(bySessionId.values());
        if (ventilation != null) {
            root.set("ventilation_summary", ventilation);
        }
        bySessionId.values().forEach(node -> nights.add("full".equals(mode) ? node : slim(node)));
        try {
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trend", e);
        }
    }

    /**
     * Builds a slim projection of a full night_analysis node.
     * Copies: date, session, respiratory_indices, sleephq (if present),
     * events (counts sub-keys only — no timed_sample), therapy (key waveform channels from channels.*).
     */
    private static ObjectNode slim(ObjectNode night) {
        ObjectNode slim = JsonApi.mapper().createObjectNode();

        copyIfPresent(night, slim, "date");
        copyIfPresent(night, slim, "session");
        copyIfPresent(night, slim, "respiratory_indices");
        copyIfPresent(night, slim, "sleephq");

        // events: copy allowed sub-keys, omit timed_sample*
        if (night.has("events")) {
            ObjectNode src = (ObjectNode) night.get("events");
            ObjectNode dest = slim.putObject("events");
            for (String key : List.of("counts", "summary_counts", "summary_counts_source",
                    "eve_total", "summary_total", "total", "event_count_authority",
                    "event_counts_agree")) {
                if (src.has(key)) {
                    dest.set(key, src.get(key).deepCopy());
                }
            }
        }

        // therapy: key waveform channels lifted from channels.*
        ObjectNode channelsNode = night.has("channels") ? (ObjectNode) night.get("channels") : null;
        if (channelsNode != null) {
            ObjectNode therapy = slim.putObject("therapy");
            for (String ch : List.of("pressure", "ipap", "epap", "leak", "leak_total",
                    "ahi", "resp_rate", "minute_vent", "tidal_volume")) {
                if (channelsNode.has(ch)) {
                    therapy.set(ch, channelsNode.get(ch).deepCopy());
                }
            }
        }

        return slim;
    }

    private static void copyIfPresent(ObjectNode src, ObjectNode dest, String key) {
        if (src.has(key)) {
            dest.set(key, src.get(key).deepCopy());
        }
    }
}
