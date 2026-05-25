package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates {@code sleep_stages_parsed} segments into agent-friendly minute totals per stage label.
 * Overlapping intervals are merged on a timeline (highest-priority stage wins per instant).
 */
public final class JournalSleepStagesSummary {

    private static final String AGGREGATION_METHOD = "merged_timeline";

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

        List<ParsedSegment> parsedSegments = new ArrayList<>();
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
            parsedSegments.add(new ParsedSegment(start, end, label));
        }

        if (parsedSegments.isEmpty()) {
            return null;
        }

        Map<String, Long> naiveSecondsByLabel = new LinkedHashMap<>();
        Instant windowStart = null;
        Instant windowEnd = null;
        for (ParsedSegment segment : parsedSegments) {
            long seconds = Duration.between(segment.start(), segment.end()).getSeconds();
            naiveSecondsByLabel.merge(segment.label(), seconds, Long::sum);
            windowStart = minInstant(windowStart, segment.start());
            windowEnd = maxInstant(windowEnd, segment.end());
        }

        Map<String, Long> mergedSecondsByLabel = mergeTimeline(parsedSegments);

        ObjectNode summary = JsonApi.mapper().createObjectNode();
        summary.put("segment_count", parsedSegments.size());
        summary.put("aggregation_method", AGGREGATION_METHOD);
        summary.set("stage_type_legend", JournalSleepStageType.legendNode());

        ObjectNode minutes = summary.putObject("minutes_by_stage");
        mergedSecondsByLabel.forEach((label, sec) -> minutes.put(label, roundMinutes(sec)));

        if (windowStart != null && windowEnd != null) {
            ObjectNode window = summary.putObject("sleep_window");
            window.put("start", windowStart.toString());
            window.put("end", windowEnd.toString());
            window.put("span_minutes", roundMinutes(Duration.between(windowStart, windowEnd).getSeconds()));
        }

        long asleepSeconds = mergedSecondsByLabel.entrySet().stream()
                .filter(e -> !"awake".equals(e.getKey()) && !"in_bed".equals(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        summary.put("asleep_minutes", roundMinutes(asleepSeconds));

        long naiveAsleepSeconds = naiveSecondsByLabel.entrySet().stream()
                .filter(e -> !"awake".equals(e.getKey()) && !"in_bed".equals(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        long spanSeconds = windowStart != null && windowEnd != null
                ? Duration.between(windowStart, windowEnd).getSeconds()
                : 0;
        boolean overlapDetected = hasOverlappingSegments(parsedSegments)
                || naiveAsleepSeconds > spanSeconds + 1
                || naiveAsleepSeconds > asleepSeconds + 1;
        summary.put("segments_overlap_detected", overlapDetected);

        return summary;
    }

    private static Map<String, Long> mergeTimeline(List<ParsedSegment> segments) {
        List<TimelineEvent> events = new ArrayList<>();
        for (ParsedSegment segment : segments) {
            events.add(new TimelineEvent(segment.start(), TimelineEventKind.START, segment.label()));
            events.add(new TimelineEvent(segment.end(), TimelineEventKind.END, segment.label()));
        }
        events.sort(Comparator
                .comparing(TimelineEvent::instant)
                .thenComparing(e -> e.kind() == TimelineEventKind.END ? 0 : 1));

        Map<String, Integer> activeCounts = new HashMap<>();
        Map<String, Long> secondsByLabel = new LinkedHashMap<>();
        Instant cursor = null;

        for (TimelineEvent event : events) {
            if (cursor != null && event.instant().isAfter(cursor)) {
                String winner = winningLabel(activeCounts);
                if (winner != null) {
                    long seconds = Duration.between(cursor, event.instant()).getSeconds();
                    secondsByLabel.merge(winner, seconds, Long::sum);
                }
            }
            adjustActive(activeCounts, event.label(), event.kind());
            cursor = event.instant();
        }

        return secondsByLabel;
    }

    private static String winningLabel(Map<String, Integer> activeCounts) {
        String winner = null;
        int bestPriority = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : activeCounts.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            int priority = JournalSleepStageType.timelinePriority(entry.getKey());
            if (priority > bestPriority) {
                bestPriority = priority;
                winner = entry.getKey();
            }
        }
        return winner;
    }

    private static void adjustActive(Map<String, Integer> activeCounts, String label, TimelineEventKind kind) {
        int delta = kind == TimelineEventKind.START ? 1 : -1;
        activeCounts.merge(label, delta, Integer::sum);
        if (activeCounts.get(label) <= 0) {
            activeCounts.remove(label);
        }
    }

    private static boolean hasOverlappingSegments(List<ParsedSegment> segments) {
        for (int i = 0; i < segments.size(); i++) {
            ParsedSegment a = segments.get(i);
            for (int j = i + 1; j < segments.size(); j++) {
                ParsedSegment b = segments.get(j);
                if (a.start().isBefore(b.end()) && b.start().isBefore(a.end())) {
                    return true;
                }
            }
        }
        return false;
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

    private record ParsedSegment(Instant start, Instant end, String label) {
    }

    private enum TimelineEventKind {
        START,
        END
    }

    private record TimelineEvent(Instant instant, TimelineEventKind kind, String label) {
    }
}
