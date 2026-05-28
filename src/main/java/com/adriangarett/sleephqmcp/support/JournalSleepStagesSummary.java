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
    private static final String AGGREGATION_MAIN_EPISODE = "main_sleep_episode";
    /** Bump when journal summary shape changes (smoke tests detect stale JVM). */
    public static final int SUMMARY_SCHEMA_VERSION = 3;
    private static final long MAIN_EPISODE_GAP_SECONDS = 30L * 60L;

    private JournalSleepStagesSummary() {
    }

    /**
     * @param parsed result of {@link JournalSleepStagesParser#tryParse(String)} (array or object with {@code stages})
     * @return summary object, or {@code null} when no usable segments
     */
    public static ObjectNode summarize(JsonNode parsed) {
        return summarize(parsed, null, null);
    }

    /**
     * @param clipStart optional CPAP BRP session start (inclusive overlap)
     * @param clipEnd optional CPAP BRP session end
     */
    public static ObjectNode summarize(JsonNode parsed, Instant clipStart, Instant clipEnd) {
        if (parsed == null || parsed.isNull() || parsed.isMissingNode()) {
            return null;
        }
        List<JsonNode> segments = extractSegments(parsed);
        boolean cpapClipApplied = false;
        if (clipStart != null && clipEnd != null && clipEnd.isAfter(clipStart)) {
            List<JsonNode> clipped = clipSegmentsToWindow(segments, clipStart, clipEnd);
            if (!clipped.isEmpty()) {
                segments = clipped;
                cpapClipApplied = true;
            }
        }
        if (segments.isEmpty()) {
            return null;
        }

        List<StageIntervalRow> rows = buildStageRows(segments);
        if (rows.isEmpty()) {
            return null;
        }

        JournalSleepStageLegend legend = resolveLegend(rows);
        List<ParsedSegment> parsedSegments = toParsedSegments(rows, legend);

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
        summary.put("summary_schema_version", SUMMARY_SCHEMA_VERSION);
        summary.put("segment_count", parsedSegments.size());
        summary.put("aggregation_method", AGGREGATION_METHOD);
        summary.put("legend_profile", legend.profileId());
        summary.set("stage_type_legend", legend.legendNode());

        ObjectNode minutes = summary.putObject("minutes_by_stage");
        mergedSecondsByLabel.forEach((label, sec) -> minutes.put(label, roundMinutes(sec)));

        ObjectNode naiveMinutes = summary.putObject("minutes_by_stage_naive");
        naiveSecondsByLabel.forEach((label, sec) -> naiveMinutes.put(label, roundMinutes(sec)));

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
        summary.put("overlap_detected", overlapDetected);

        EpisodeWindow mainEpisode = findMainSleepEpisode(parsedSegments);
        if (mainEpisode != null) {
            List<ParsedSegment> episodeSegments = filterToWindow(parsedSegments, mainEpisode.start(), mainEpisode.end());
            Map<String, Long> episodeMerged = mergeTimeline(episodeSegments);
            ObjectNode episodeMinutes = summary.putObject("minutes_by_stage_main_episode");
            episodeMerged.forEach((label, sec) -> episodeMinutes.put(label, roundMinutes(sec)));
            summary.put("aggregation_method_episode", AGGREGATION_MAIN_EPISODE);
            ObjectNode episodeWindow = summary.putObject("main_episode_window");
            episodeWindow.put("start", mainEpisode.start().toString());
            episodeWindow.put("end", mainEpisode.end().toString());
            episodeWindow.put("span_minutes", roundMinutes(Duration.between(mainEpisode.start(), mainEpisode.end()).getSeconds()));

            boolean mismatch = isStageMismatch(mergedSecondsByLabel, episodeMerged, spanSeconds, overlapDetected);
            summary.put("journal_stage_mismatch", mismatch);
            if (mismatch) {
                summary.put("ui_parity_note",
                        "Full-span merged_timeline totals can disagree with SleepHQ dashboard cards when Apple Health "
                                + "segments span wider than the main sleep episode. Prefer minutes_by_stage_for_reporting "
                                + "or minutes_by_stage_main_episode for wellness narrative; cite sleep_window span when "
                                + "overlap_detected is true.");
            }
        }

        attachReportingTotals(summary, mergedSecondsByLabel, naiveSecondsByLabel, legend);
        if (cpapClipApplied) {
            summary.put("cpap_session_clipped", true);
            ObjectNode clipWindow = summary.putObject("cpap_session_window");
            clipWindow.put("start", clipStart.toString());
            clipWindow.put("end", clipEnd.toString());
        }

        return summary;
    }

    private static List<StageIntervalRow> buildStageRows(List<JsonNode> segments) {
        List<StageIntervalRow> rows = new ArrayList<>();
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
            long seconds = Duration.between(start, end).getSeconds();
            rows.add(new StageIntervalRow(start, end, stageType, seconds));
        }
        return rows;
    }

    private static JournalSleepStageLegend resolveLegend(List<StageIntervalRow> rows) {
        List<JournalSleepStageLegend.StageInterval> intervals = rows.stream()
                .map(r -> new JournalSleepStageLegend.StageInterval(r.stageType(), r.durationSeconds()))
                .toList();
        Map<String, Long> hk = JournalSleepStageLegend.naiveSecondsByLabel(intervals, JournalSleepStageLegend.APPLE_HEALTH);
        Map<String, Long> shq = JournalSleepStageLegend.naiveSecondsByLabel(intervals, JournalSleepStageLegend.SLEEPHQ_DASHBOARD);
        return JournalSleepStageLegend.detect(hk, shq);
    }

    private static List<ParsedSegment> toParsedSegments(List<StageIntervalRow> rows, JournalSleepStageLegend legend) {
        List<ParsedSegment> parsedSegments = new ArrayList<>();
        for (StageIntervalRow row : rows) {
            parsedSegments.add(new ParsedSegment(row.start(), row.end(), legend.labelFor(row.stageType())));
        }
        return parsedSegments;
    }

    private static void attachReportingTotals(ObjectNode summary,
                                              Map<String, Long> fullMerged,
                                              Map<String, Long> naiveSeconds,
                                              JournalSleepStageLegend legend) {
        ObjectNode reporting = JsonApi.mapper().createObjectNode();
        String source;
        if (legend == JournalSleepStageLegend.SLEEPHQ_DASHBOARD) {
            naiveSeconds.forEach((label, sec) -> reporting.put(label, roundMinutes(sec)));
            source = "sleephq_dashboard_naive";
            summary.put("journal_stage_mismatch", false);
            summary.put("ui_parity_note",
                    "Totals use SleepHQ dashboard stage_type encoding (2=deep, 4=rem, 5=awake), not Apple Health HK.");
        } else {
            JsonNode episodeNode = summary.get("minutes_by_stage_main_episode");
            boolean mismatch = summary.path("journal_stage_mismatch").asBoolean(false);
            if (episodeNode != null && episodeNode.isObject() && mismatch) {
                episodeNode.properties().forEach(e -> reporting.put(e.getKey(), e.getValue().asDouble()));
                source = AGGREGATION_MAIN_EPISODE;
            } else {
                fullMerged.forEach((label, sec) -> reporting.put(label, roundMinutes(sec)));
                source = AGGREGATION_METHOD;
            }
        }
        summary.set("minutes_by_stage_for_reporting", reporting);
        summary.put("reporting_source", source);
    }

    private static List<JsonNode> clipSegmentsToWindow(List<JsonNode> segments, Instant clipStart, Instant clipEnd) {
        List<JsonNode> clipped = new ArrayList<>();
        for (JsonNode segment : segments) {
            Instant start = parseInstant(segment, "started_at", "start");
            Instant end = parseInstant(segment, "ended_at", "end");
            if (start == null || end == null || !end.isAfter(start)) {
                continue;
            }
            if (end.isAfter(clipStart) && start.isBefore(clipEnd)) {
                clipped.add(segment);
            }
        }
        return clipped;
    }

    private static boolean isStageMismatch(Map<String, Long> fullMerged,
                                           Map<String, Long> episodeMerged,
                                           long spanSeconds,
                                           boolean overlapDetected) {
        double fullAwake = roundMinutes(fullMerged.getOrDefault("awake", 0L));
        double episodeAwake = roundMinutes(episodeMerged.getOrDefault("awake", 0L));
        double fullRem = roundMinutes(fullMerged.getOrDefault("rem", 0L));
        double episodeRem = roundMinutes(episodeMerged.getOrDefault("rem", 0L));
        boolean awakeInflated = fullAwake - episodeAwake >= 30.0;
        boolean remDeflated = episodeRem - fullRem >= 60.0;
        boolean spanWide = spanSeconds > 12 * 3600L;
        return overlapDetected || awakeInflated || remDeflated || spanWide;
    }

    private static EpisodeWindow findMainSleepEpisode(List<ParsedSegment> segments) {
        if (segments.isEmpty()) {
            return null;
        }
        List<ParsedSegment> sorted = segments.stream()
                .sorted(Comparator.comparing(ParsedSegment::start))
                .toList();

        List<EpisodeWindow> episodes = new ArrayList<>();
        Instant episodeStart = sorted.getFirst().start();
        Instant episodeEnd = sorted.getFirst().end();

        for (int i = 1; i < sorted.size(); i++) {
            ParsedSegment segment = sorted.get(i);
            long gapSeconds = Duration.between(episodeEnd, segment.start()).getSeconds();
            if (gapSeconds > MAIN_EPISODE_GAP_SECONDS) {
                episodes.add(new EpisodeWindow(episodeStart, episodeEnd));
                episodeStart = segment.start();
                episodeEnd = segment.end();
            } else {
                episodeEnd = maxInstant(episodeEnd, segment.end());
            }
        }
        episodes.add(new EpisodeWindow(episodeStart, episodeEnd));

        EpisodeWindow best = null;
        long bestAsleep = -1;
        for (EpisodeWindow episode : episodes) {
            long asleep = asleepSecondsInWindow(sorted, episode.start(), episode.end());
            if (asleep > bestAsleep) {
                bestAsleep = asleep;
                best = episode;
            }
        }
        return best;
    }

    private static long asleepSecondsInWindow(List<ParsedSegment> segments, Instant start, Instant end) {
        long total = 0;
        for (ParsedSegment segment : segments) {
            if ("awake".equals(segment.label()) || "in_bed".equals(segment.label())) {
                continue;
            }
            Instant overlapStart = segment.start().isBefore(start) ? start : segment.start();
            Instant overlapEnd = segment.end().isAfter(end) ? end : segment.end();
            if (overlapEnd.isAfter(overlapStart)) {
                total += Duration.between(overlapStart, overlapEnd).getSeconds();
            }
        }
        return total;
    }

    private static List<ParsedSegment> filterToWindow(List<ParsedSegment> segments, Instant start, Instant end) {
        List<ParsedSegment> out = new ArrayList<>();
        for (ParsedSegment segment : segments) {
            if (segment.end().isAfter(start) && segment.start().isBefore(end)) {
                Instant clipStart = segment.start().isBefore(start) ? start : segment.start();
                Instant clipEnd = segment.end().isAfter(end) ? end : segment.end();
                out.add(new ParsedSegment(clipStart, clipEnd, segment.label()));
            }
        }
        return out;
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

    private record StageIntervalRow(Instant start, Instant end, int stageType, long durationSeconds) {
    }

    private record ParsedSegment(Instant start, Instant end, String label) {
    }

    private enum TimelineEventKind {
        START,
        END
    }

    private record TimelineEvent(Instant instant, TimelineEventKind kind, String label) {
    }

    private record EpisodeWindow(Instant start, Instant end) {
    }
}
