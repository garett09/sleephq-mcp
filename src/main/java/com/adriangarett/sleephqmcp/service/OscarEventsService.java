package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.oscar.OscarEventSummaryBuilder;
import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class OscarEventsService {

    private final OscarRepository oscarRepository;

    public OscarEventsService(OscarRepository oscarRepository) {
        this.oscarRepository = oscarRepository;
    }

    public String eventsForDate(String calendarDate, String detail) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        String mode = detail == null || detail.isBlank() ? "summary" : detail.toLowerCase(Locale.ROOT);
        LocalDate localDate = LocalDate.parse(date);
        Optional<Map<String, Integer>> summaryCounts = oscarRepository.loadSummaryEventCounts(localDate);
        return oscarRepository.loadEventsByDate(localDate)
                .map(result -> serialize(result, mode, date, summaryCounts))
                .or(() -> summaryCounts.map(counts -> serializeSummaryOnly(date, counts)))
                .orElseGet(() -> JsonApi.toJsonString(java.util.Map.of(
                        "date", date,
                        "oscar_status", "unavailable")));
    }

    private String serializeSummaryOnly(String date, Map<String, Integer> summaryCounts) {
        try {
            ObjectNode root = JsonApi.mapper().createObjectNode();
            root.put("date", date);
            root.set("event_summary", OscarEventSummaryBuilder.buildSummaryOnly(summaryCounts));
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize summary event counts", e);
        }
    }

    private String serialize(
            DeviceEventResult result,
            String mode,
            String date,
            Optional<Map<String, Integer>> summaryCounts) {
        try {
            ObjectNode root = JsonApi.mapper().createObjectNode();
            root.put("date", date);
            int maxTimed = oscarRepository.properties().analysis() != null
                    ? oscarRepository.properties().analysis().maxTimedEvents()
                    : 100;
            root.set("event_summary", OscarEventSummaryBuilder.buildSummary(result, maxTimed, summaryCounts));
            if ("full".equals(mode)) {
                root.set("events", JsonApi.mapper().valueToTree(result.events()));
            }
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize events", e);
        }
    }
}
