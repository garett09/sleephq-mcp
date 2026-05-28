package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.support.JournalSleepStageType;
import com.adriangarett.sleephqmcp.support.JournalSleepStagesParser;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.WaveformAnchorSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps journal sleep-stage segments overlapping the CPAP BRP session window to session-relative minutes.
 */
public final class JournalSleepStageAlignment {

    private JournalSleepStageAlignment() {
    }

    public static Optional<ObjectNode> align(String teamId,
                                             String date,
                                             String stageAnchor,
                                             JournalLookupService journalLookup,
                                             WaveformService waveformService,
                                             Integer cpapClockAdjustSeconds) {
        Optional<JsonNode> journalAttrs = journalLookup.findAttributesByDate(teamId, date);
        if (journalAttrs.isEmpty()) {
            return Optional.empty();
        }
        JsonNode stagesRaw = journalAttrs.get().path("sleep_stages");
        if (stagesRaw.isMissingNode() || stagesRaw.isNull()) {
            return Optional.empty();
        }
        JsonNode parsed = JournalSleepStagesParser.tryParse(stagesRaw.asText());
        if (parsed == null) {
            return Optional.empty();
        }

        ApneaScanResult scan;
        try {
            scan = waveformService.loadScanByDate(teamId, date, cpapClockAdjustSeconds);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        LocalDateTime sessionStart = LocalDateTime.parse(scan.startDatetime().substring(0, 19));
        Instant sessionStartInstant = sessionStart.toInstant(ZoneOffset.UTC);
        Instant sessionEndInstant = sessionStartInstant.plusSeconds((long) scan.durationSeconds());

        String stageLabel = stageAnchor.toLowerCase(Locale.ROOT);
        List<Overlap> overlaps = new ArrayList<>();
        for (JsonNode segment : extractSegments(parsed)) {
            Instant segStart = parseInstant(segment);
            Instant segEnd = parseInstantEnd(segment);
            if (segStart == null || segEnd == null || !segEnd.isAfter(segStart)) {
                continue;
            }
            int stageType = segment.path("stage_type").asInt(segment.path("stage").asInt(-1));
            String label = JournalSleepStageType.labelFor(stageType);
            if (!stageLabel.equals(label)) {
                continue;
            }
            Instant overlapStart = segStart.isBefore(sessionStartInstant) ? sessionStartInstant : segStart;
            Instant overlapEnd = segEnd.isAfter(sessionEndInstant) ? sessionEndInstant : segEnd;
            if (!overlapEnd.isAfter(overlapStart)) {
                continue;
            }
            long overlapSeconds = Duration.between(overlapStart, overlapEnd).getSeconds();
            Instant midpoint = overlapStart.plusSeconds(overlapSeconds / 2);
            long sessionOffsetSeconds = Duration.between(sessionStartInstant, midpoint).getSeconds();
            overlaps.add(new Overlap(overlapSeconds, sessionOffsetSeconds));
        }

        if (overlaps.isEmpty()) {
            return Optional.empty();
        }

        Overlap best = overlaps.stream()
                .max((a, b) -> Long.compare(a.overlapSeconds(), b.overlapSeconds()))
                .orElseThrow();

        String confidence = best.overlapSeconds() >= 300 ? "high"
                : best.overlapSeconds() >= 60 ? "medium" : "low";

        int startSeconds = WaveformAnchorSupport.startSecondsFromEventSeconds(best.sessionOffsetSeconds());
        int startMinute = WaveformAnchorSupport.startMinuteFromEventSeconds(best.sessionOffsetSeconds());

        ObjectNode result = JsonApi.mapper().createObjectNode();
        result.put("start_minute", startMinute);
        result.put("start_seconds", startSeconds);
        result.put("alignment_confidence", confidence);
        result.put("reason", "Journal " + stageLabel + " segment overlaps CPAP session (overlap "
                + (best.overlapSeconds() / 60) + " min); window at session minute " + startMinute + ".");
        return Optional.of(result);
    }

    private static List<JsonNode> extractSegments(JsonNode parsed) {
        List<JsonNode> out = new ArrayList<>();
        if (parsed.isArray()) {
            parsed.forEach(out::add);
        } else {
            parsed.path("stages").forEach(out::add);
        }
        return out;
    }

    private static Instant parseInstant(JsonNode segment) {
        String text = segment.path("started_at").asText(null);
        if (text == null || text.isBlank()) {
            text = segment.path("start").asText(null);
        }
        return text == null || text.isBlank() ? null : Instant.parse(text);
    }

    private static Instant parseInstantEnd(JsonNode segment) {
        String text = segment.path("ended_at").asText(null);
        if (text == null || text.isBlank()) {
            text = segment.path("end").asText(null);
        }
        return text == null || text.isBlank() ? null : Instant.parse(text);
    }

    private record Overlap(long overlapSeconds, long sessionOffsetSeconds) {
    }
}
