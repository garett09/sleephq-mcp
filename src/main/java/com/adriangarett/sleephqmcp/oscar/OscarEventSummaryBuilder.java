package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OscarEventSummaryBuilder {

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
        List<DeviceEvent> therapyOnly = new ArrayList<>();
        int therapyEvents = 0;
        for (DeviceEvent event : result.events()) {
            String key = OscarEventLabelCanonicalizer.canonical(event.label());
            if (key == null) {
                continue;
            }
            counts.merge(key, 1, Integer::sum);
            therapyEvents++;
            therapyOnly.add(event);
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
        if (hasSummaryCounts) {
            root.put("event_count_authority", "oscar_summary_000");
            root.put("event_counts_agree", therapyEvents == summaryTotal);
        } else if (therapyEvents > 0) {
            root.put("event_count_authority", "oscar_eve_edf");
        }
        ArrayNode timed = root.putArray("timed_sample");
        int limit = Math.min(maxTimedEvents, therapyOnly.size());
        for (int i = 0; i < limit; i++) {
            DeviceEvent event = therapyOnly.get(i);
            ObjectNode row = timed.addObject();
            row.put("timestamp", event.timestamp());
            row.put("label", event.label());
            row.put("code", event.code());
            row.put("duration_seconds", event.durationSeconds());
        }
        if (therapyOnly.size() > limit) {
            root.put("timed_sample_truncated", true);
            root.put("timed_sample_total", therapyOnly.size());
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
}
