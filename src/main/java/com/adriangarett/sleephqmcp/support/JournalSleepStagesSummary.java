package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code sleep_stages_parsed} segments into agent-friendly minute totals per stage label.
 */
public final class JournalSleepStagesSummary {

    private JournalSleepStagesSummary() {
    }

    /**
     * @param parsed result of {@link JournalSleepStagesParser#tryParse(String)} (array or object with {@code stages})
     * @return summary object, or {@code null} when no usable segments
     */
    public static ObjectNode summarize(JsonNode parsed) {
        if (parsed == null || parsed.isNull() || parsed.isMissingNode()) {
            return null;
        }
        List<JsonNode> segments = extractSegments(parsed);
        if (segments.isEmpty()) {
            return null;
        }

        Map<String, Long> secondsByLabel = new LinkedHashMap<>();
        Instant windowStart = null;
        Instant windowEnd = null;
        int counted = 0;

        for (JsonNode segment : segments) {
            Instant start = parseInstant(segment, "started_at", "start");
            Instant end = parseInstant(segment, "ended_at", "end");
            if (start == null || end == null || !end.isAfter(start)) {
                continue;
            }
            int stageType = segment.path("stage_type").asInt(segment.path("stage").asInt(-1));
            if (stageType < 0) {
                continue;
            }
            String label = JournalSleepStageType.labelFor(stageType);
            long seconds = java.time.Duration.between(start, end).getSeconds();
            secondsByLabel.merge(label, seconds, Long::sum);
            windowStart = minInstant(windowStart, start);
            windowEnd = maxInstant(windowEnd, end);
            counted++;
        }

        if (counted == 0) {
            return null;
        }

        ObjectNode summary = JsonApi.mapper().createObjectNode();
        summary.put("segment_count", counted);
        summary.set("stage_type_legend", JournalSleepStageType.legendNode());

        ObjectNode minutes = summary.putObject("minutes_by_stage");
        secondsByLabel.forEach((label, sec) -> minutes.put(label, roundMinutes(sec)));

        if (windowStart != null && windowEnd != null) {
            ObjectNode window = summary.putObject("sleep_window");
            window.put("start", windowStart.toString());
            window.put("end", windowEnd.toString());
            window.put("span_minutes", roundMinutes(java.time.Duration.between(windowStart, windowEnd).getSeconds()));
        }

        long asleepSeconds = secondsByLabel.entrySet().stream()
                .filter(e -> !"awake".equals(e.getKey()) && !"in_bed".equals(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        summary.put("asleep_minutes", roundMinutes(asleepSeconds));

        return summary;
    }

    private static List<JsonNode> extractSegments(JsonNode parsed) {
        List<JsonNode> out = new ArrayList<>();
        if (parsed.isArray()) {
            parsed.forEach(out::add);
            return out;
        }
        JsonNode stages = parsed.path("stages");
        if (stages.isArray()) {
            stages.forEach(out::add);
        }
        return out;
    }

    private static Instant parseInstant(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            String text = node.path(name).asText(null);
            if (text != null && !text.isBlank()) {
                try {
                    return Instant.parse(text);
                } catch (Exception ignored) {
                    // try next field
                }
            }
        }
        return null;
    }

    private static Instant minInstant(Instant current, Instant candidate) {
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }

    private static Instant maxInstant(Instant current, Instant candidate) {
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private static double roundMinutes(long seconds) {
        return Math.round(seconds / 60.0 * 10.0) / 10.0;
    }
}
