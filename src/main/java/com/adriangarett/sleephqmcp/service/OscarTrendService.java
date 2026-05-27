package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OscarTrendService {

    private final OscarRepository oscarRepository;
    private final UnifiedNightAnalysisService nightAnalysisService;

    public OscarTrendService(OscarRepository oscarRepository, UnifiedNightAnalysisService nightAnalysisService) {
        this.oscarRepository = oscarRepository;
        this.nightAnalysisService = nightAnalysisService;
    }

    public String trend(int days) {
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("days must be between 1 and 90");
        }
        LocalDate end = oscarRepository.getLastSessionDate().orElse(LocalDate.now());
        LocalDate start = end.minusDays(days - 1L);
        return serializeTrend(start, end);
    }

    public String trend(String endDate, int days) {
        LocalDate end = LocalDate.parse(SleepHqPathParams.requireCalendarDate(endDate, "endDate"));
        LocalDate start = end.minusDays(days - 1L);
        return serializeTrend(start, end);
    }

    /**
     * One row per OSCAR session in the calendar window ({@code start}..{@code end} inclusive).
     * Walks each calendar day; when a session spans multiple days, the latest in-range date wins
     * (matches {@code get-combined-night-by-date} end-night queries).
     */
    private String serializeTrend(LocalDate start, LocalDate end) {
        ObjectNode root = JsonApi.mapper().createObjectNode();
        root.put("start_date", start.toString());
        root.put("end_date", end.toString());
        ArrayNode nights = root.putArray("nights");
        Map<String, ObjectNode> bySessionId = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            nightAnalysisService.analyzeNight(date.toString()).ifPresent(node -> {
                String sessionId = node.path("session").path("session_id").asText("");
                if (!sessionId.isBlank()) {
                    bySessionId.put(sessionId, node);
                }
            });
        }
        bySessionId.values().forEach(nights::add);
        try {
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trend", e);
        }
    }
}
