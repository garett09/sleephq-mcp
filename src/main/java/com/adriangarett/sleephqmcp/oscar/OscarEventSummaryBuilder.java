package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OscarEventSummaryBuilder {

    private static final Set<String> NON_THERAPY_EVENT_KEYS = Set.of(
            "recording_start",
            "recording_starts",
            "recording_end",
            "recording_stop",
            "starting",
            "stopping",
            "start",
            "stop",
            "unknown");

    private OscarEventSummaryBuilder() {}

    public static ObjectNode buildSummary(DeviceEventResult result, int maxTimedEvents) {
        return buildSummary(result, maxTimedEvents, Optional.empty());
    }

    public static ObjectNode buildSummary(
            DeviceEventResult result,
            int maxTimedEvents,
            Optional<Map<String, Integer>> summaryCounts) {
        ObjectNode root = com.adriangarett.sleephqmcp.support.JsonApi.mapper().createObjectNode();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int therapyEvents = 0;
        for (DeviceEvent event : result.events()) {
            String key = normalizeLabel(event.label());
            if (isNonTherapyEvent(key)) {
                continue;
            }
            counts.merge(key, 1, Integer::sum);
            therapyEvents++;
        }
        ObjectNode countsNode = root.putObject("counts");
        counts.forEach(countsNode::put);
        root.put("eve_total", therapyEvents);
        int summaryTotal = 0;
        boolean hasSummaryCounts = false;
        if (summaryCounts.isPresent() && !summaryCounts.get().isEmpty()) {
            hasSummaryCounts = true;
            ObjectNode summaryNode = root.putObject("summary_counts");
            summaryCounts.get().forEach(summaryNode::put);
            root.put("summary_counts_source", "oscar_summary_000");
            summaryTotal = summaryCounts.get().values().stream().mapToInt(Integer::intValue).sum();
            root.put("summary_total", summaryTotal);
        }
        root.put("total", hasSummaryCounts ? summaryTotal : therapyEvents);
        if (hasSummaryCounts && therapyEvents < summaryTotal) {
            root.put("event_count_authority", "oscar_summary_000");
        } else if (therapyEvents > 0) {
            root.put("event_count_authority", "oscar_eve_edf");
        }
        ArrayNode timed = root.putArray("timed_sample");
        List<DeviceEvent> events = result.events();
        int limit = Math.min(maxTimedEvents, events.size());
        for (int i = 0; i < limit; i++) {
            DeviceEvent event = events.get(i);
            ObjectNode row = timed.addObject();
            row.put("timestamp", event.timestamp());
            row.put("label", event.label());
            row.put("code", event.code());
            row.put("duration_seconds", event.durationSeconds());
        }
        if (events.size() > limit) {
            root.put("timed_sample_truncated", true);
            root.put("timed_sample_total", events.size());
        }
        return root;
    }

    public static ObjectNode buildSummaryOnly(Map<String, Integer> summaryCounts) {
        ObjectNode root = com.adriangarett.sleephqmcp.support.JsonApi.mapper().createObjectNode();
        ObjectNode summaryNode = root.putObject("summary_counts");
        summaryCounts.forEach(summaryNode::put);
        root.put("summary_counts_source", "oscar_summary_000");
        int summaryTotal = summaryCounts.values().stream().mapToInt(Integer::intValue).sum();
        root.put("summary_total", summaryTotal);
        root.putObject("counts");
        root.put("eve_total", 0);
        root.put("total", summaryTotal);
        root.put("event_count_authority", "oscar_summary_000");
        return root;
    }

    static boolean isNonTherapyEvent(String normalizedLabel) {
        if (normalizedLabel == null || normalizedLabel.isBlank()) {
            return true;
        }
        if (NON_THERAPY_EVENT_KEYS.contains(normalizedLabel)) {
            return true;
        }
        return normalizedLabel.startsWith("recording");
    }

    private static String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "unknown";
        }
        return label.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
