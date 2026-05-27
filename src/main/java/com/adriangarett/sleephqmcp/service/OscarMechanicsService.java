package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class OscarMechanicsService {

    private final UnifiedNightAnalysisService nightAnalysisService;

    public OscarMechanicsService(UnifiedNightAnalysisService nightAnalysisService) {
        this.nightAnalysisService = nightAnalysisService;
    }

    public String mechanicsForDate(String calendarDate) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        return nightAnalysisService.analyzeNight(date)
                .map(analysis -> {
                    ObjectNode out = JsonApi.mapper().createObjectNode();
                    out.put("date", date);
                    if (analysis.has("channels")) {
                        out.set("channels", analysis.get("channels"));
                    }
                    if (analysis.has("respiratory_indices")) {
                        out.set("respiratory_indices", analysis.get("respiratory_indices"));
                    }
                    try {
                        return JsonApi.mapper().writeValueAsString(out);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .orElseGet(() -> JsonApi.toJsonString(java.util.Map.of(
                        "date", date,
                        "oscar_status", "unavailable")));
    }
}
