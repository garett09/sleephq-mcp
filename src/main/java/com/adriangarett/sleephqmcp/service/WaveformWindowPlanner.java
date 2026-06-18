package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.ApneaEvent;
import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.domain.WaveformWindowPlan;
import com.adriangarett.sleephqmcp.domain.WindowEvidence;
import com.adriangarett.sleephqmcp.support.WaveformAnchorSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

@Service
public class WaveformWindowPlanner {

    /** Upper bound on a manual startMinute so {@code startMinute * 60} cannot overflow int. */
    private static final int MAX_START_MINUTE = 24 * 60;

    private final DeviceEventService deviceEventService;
    private final WaveformService waveformService;
    private final UnifiedNightAnalysisService nightAnalysisService;
    private final ClinicalContextProperties clinical;
    private final JournalLookupService journalLookupService;

    public WaveformWindowPlanner(DeviceEventService deviceEventService,
                                   WaveformService waveformService,
                                   UnifiedNightAnalysisService nightAnalysisService,
                                   ClinicalContextProperties clinical,
                                   JournalLookupService journalLookupService) {
        this.deviceEventService = deviceEventService;
        this.waveformService = waveformService;
        this.nightAnalysisService = nightAnalysisService;
        this.clinical = clinical;
        this.journalLookupService = journalLookupService;
    }

    public WaveformWindowPlan plan(String teamId,
                                   String date,
                                   String anchor,
                                   int maxWindows,
                                   int windowIndex,
                                   Integer manualStartMinute,
                                   int maxMinutes,
                                   Integer cpapClockAdjustSeconds) {
        validateWindowParams(maxWindows, windowIndex);
        String anchorRequested = anchor == null || anchor.isBlank()
                ? WaveformAnchorSupport.ANCHOR_AUTO
                : anchor.trim().toLowerCase(Locale.ROOT);

        if (manualStartMinute != null) {
            if (manualStartMinute < 0 || manualStartMinute > MAX_START_MINUTE) {
                throw new IllegalArgumentException(
                        "startMinute must be between 0 and " + MAX_START_MINUTE);
            }
            int startSeconds = manualStartMinute * 60;
            return new WaveformWindowPlan(
                    anchorRequested,
                    WaveformAnchorSupport.ANCHOR_MANUAL,
                    manualStartMinute,
                    startSeconds,
                    0,
                    maxMinutes,
                    "Manual startMinute override; anchor ignored.",
                    List.of(),
                    null);
        }

        if (WaveformAnchorSupport.STAGE_ANCHORS.contains(anchorRequested)) {
            return planStageAnchor(teamId, date, anchorRequested, maxMinutes, cpapClockAdjustSeconds);
        }

        if (!WaveformAnchorSupport.V1_EVENT_ANCHORS.contains(anchorRequested)) {
            throw new IllegalArgumentException("invalid_anchor: unknown anchor " + anchorRequested);
        }

        NightContext ctx = loadNightContext(teamId, date, cpapClockAdjustSeconds);

        if (WaveformAnchorSupport.ANCHOR_AUTO.equals(anchorRequested)) {
            List<WaveformWindowPlan> candidates = buildAutoCandidates(ctx, anchorRequested, maxMinutes);
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("no_anchor_candidates: no clinical anchor for date " + date);
            }
            return selectAutoWindow(candidates, maxWindows, windowIndex);
        }

        Optional<WaveformWindowPlan> resolved = resolveExplicitAnchor(anchorRequested, ctx, anchorRequested, maxMinutes);
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("no_anchor_candidates: anchor " + anchorRequested + " had no match for date " + date);
        }
        return resolved.get();
    }

    private void validateWindowParams(int maxWindows, int windowIndex) {
        if (maxWindows != 1 && maxWindows != 2) {
            throw new IllegalArgumentException("maxWindows must be 1 or 2");
        }
        if (windowIndex != 0 && windowIndex != 1) {
            throw new IllegalArgumentException("windowIndex must be 0 or 1");
        }
        if (maxWindows == 1 && windowIndex != 0) {
            throw new IllegalArgumentException("windowIndex must be 0 when maxWindows=1");
        }
    }

    private NightContext loadNightContext(String teamId, String date, Integer cpapClockAdjustSeconds) {
        waveformService.resolveBrpFileId(requireTeamId(teamId), date);

        DeviceEventResult eve = tryLoadEve(teamId, date, cpapClockAdjustSeconds);
        ApneaScanResult scan = waveformService.loadScanByDate(teamId, date, cpapClockAdjustSeconds);
        Optional<ObjectNode> nightAnalysis = nightAnalysisService.analyzeNight(date);

        return new NightContext(eve, scan, nightAnalysis.orElse(null));
    }

    private DeviceEventResult tryLoadEve(String teamId, String date, Integer cpapClockAdjustSeconds) {
        try {
            return deviceEventService.loadDeviceEventsByDate(teamId, date, cpapClockAdjustSeconds);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("No file matching")) {
                return new DeviceEventResult("", "", 0, "eve", List.of());
            }
            throw e;
        }
    }

    private List<WaveformWindowPlan> buildAutoCandidates(NightContext ctx, String anchorRequested, int maxMinutes) {
        List<WaveformWindowPlan> out = new ArrayList<>();
        addIfPresent(out, resolveEveScanOverlap(ctx, anchorRequested, maxMinutes));
        addIfPresent(out, resolveWorstObstructive(ctx, anchorRequested, maxMinutes));
        addIfPresent(out, resolveWorstCentral(ctx, anchorRequested, maxMinutes));
        addIfPresent(out, resolveWorstLeak(ctx, anchorRequested, maxMinutes));
        addIfPresent(out, resolveNotableMoment(ctx, anchorRequested, maxMinutes));
        return out;
    }

    private static void addIfPresent(List<WaveformWindowPlan> list, Optional<WaveformWindowPlan> plan) {
        plan.ifPresent(list::add);
    }

    private WaveformWindowPlan selectAutoWindow(List<WaveformWindowPlan> candidates, int maxWindows, int windowIndex) {
        if (windowIndex == 0 || maxWindows == 1) {
            return candidates.getFirst();
        }
        WaveformWindowPlan primary = candidates.getFirst();
        for (int i = 1; i < candidates.size(); i++) {
            WaveformWindowPlan candidate = candidates.get(i);
            if (Math.abs(candidate.startMinute() - primary.startMinute()) >= WaveformAnchorSupport.SECOND_WINDOW_MIN_GAP_MINUTES) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("no_anchor_candidates: second window requires clusters >= "
                + WaveformAnchorSupport.SECOND_WINDOW_MIN_GAP_MINUTES + " min apart");
    }

    private Optional<WaveformWindowPlan> resolveExplicitAnchor(String anchorId, NightContext ctx,
                                                               String anchorRequested, int maxMinutes) {
        return switch (anchorId) {
            case WaveformAnchorSupport.ANCHOR_EVE_SCAN_OVERLAP -> resolveEveScanOverlap(ctx, anchorRequested, maxMinutes);
            case WaveformAnchorSupport.ANCHOR_WORST_OBSTRUCTIVE -> resolveWorstObstructive(ctx, anchorRequested, maxMinutes);
            case WaveformAnchorSupport.ANCHOR_WORST_CENTRAL -> resolveWorstCentral(ctx, anchorRequested, maxMinutes);
            case WaveformAnchorSupport.ANCHOR_WORST_LEAK -> resolveWorstLeak(ctx, anchorRequested, maxMinutes);
            case WaveformAnchorSupport.ANCHOR_NOTABLE_MOMENT -> resolveNotableMoment(ctx, anchorRequested, maxMinutes);
            default -> Optional.empty();
        };
    }

    private Optional<WaveformWindowPlan> resolveEveScanOverlap(NightContext ctx, String anchorRequested, int maxMinutes) {
        List<DeviceEvent> eveEvents = clinicalEveEvents(ctx.eve());
        List<ApneaEvent> scanEvents = ctx.scan().events();
        if (eveEvents.isEmpty() || scanEvents.isEmpty()) {
            return Optional.empty();
        }

        OverlapPick best = null;
        for (DeviceEvent eve : eveEvents) {
            for (ApneaEvent scan : scanEvents) {
                double delta = Math.abs(eve.startSeconds() - scan.startSeconds());
                if (delta > WaveformAnchorSupport.EVE_SCAN_OVERLAP_SECONDS) {
                    continue;
                }
                double score = eve.durationSeconds() + obstructiveBonus(eve);
                if (best == null || score > best.score()) {
                    List<WindowEvidence> evidence = List.of(
                            eveEvidence(eve),
                            scanEvidence(scan));
                    best = new OverlapPick(eve, scan, score, evidence);
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(buildPlan(
                anchorRequested,
                WaveformAnchorSupport.ANCHOR_EVE_SCAN_OVERLAP,
                best.eve().startSeconds(),
                "EVE " + best.eve().label() + " aligned with scan within "
                        + WaveformAnchorSupport.EVE_SCAN_OVERLAP_SECONDS + "s at session offset "
                        + best.eve().offset() + ".",
                best.evidence(),
                maxMinutes,
                null));
    }

    private Optional<WaveformWindowPlan> resolveWorstObstructive(NightContext ctx, String anchorRequested, int maxMinutes) {
        return clinicalEveEvents(ctx.eve()).stream()
                .filter(WaveformWindowPlanner::isObstructiveEvent)
                .max(Comparator.comparingDouble(DeviceEvent::durationSeconds))
                .map(eve -> buildPlan(
                        anchorRequested,
                        WaveformAnchorSupport.ANCHOR_WORST_OBSTRUCTIVE,
                        eve.startSeconds(),
                        "Worst obstructive EVE event: " + eve.label() + " (" + formatDuration(eve.durationSeconds()) + "s).",
                        List.of(eveEvidence(eve)),
                        maxMinutes,
                        null));
    }

    private Optional<WaveformWindowPlan> resolveWorstCentral(NightContext ctx, String anchorRequested, int maxMinutes) {
        return clinicalEveEvents(ctx.eve()).stream()
                .filter(WaveformWindowPlanner::isCentralEvent)
                .max(Comparator.comparingDouble(DeviceEvent::durationSeconds))
                .map(eve -> buildPlan(
                        anchorRequested,
                        WaveformAnchorSupport.ANCHOR_WORST_CENTRAL,
                        eve.startSeconds(),
                        "Worst central EVE event: " + eve.label() + " (" + formatDuration(eve.durationSeconds()) + "s).",
                        List.of(eveEvidence(eve)),
                        maxMinutes,
                        null));
    }

    private Optional<WaveformWindowPlan> resolveWorstLeak(NightContext ctx, String anchorRequested, int maxMinutes) {
        if (ctx.nightAnalysis() == null) {
            return Optional.empty();
        }
        ArrayNode moments = ctx.nightAnalysis().withArray("notable_moments");
        JsonNode best = null;
        for (JsonNode moment : moments) {
            String channel = moment.path("channel").asText("").toLowerCase(Locale.ROOT);
            if (!channel.contains("leak")) {
                continue;
            }
            // "Peak leak" must never come from a min-kind (low) leak moment winning the max-value
            // comparison and getting mislabeled as the peak. Exclude explicit min-kind; max-kind and
            // un-kinded moments remain eligible.
            if ("min".equals(moment.path("kind").asText())) {
                continue;
            }
            if (best == null || moment.path("value").asDouble() > best.path("value").asDouble()) {
                best = moment;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        OptionalLong offset = offsetSeconds(best);
        if (offset.isEmpty()) {
            return Optional.empty();
        }
        long startSeconds = offset.getAsLong();
        return Optional.of(buildPlan(
                anchorRequested,
                WaveformAnchorSupport.ANCHOR_WORST_LEAK,
                startSeconds,
                "Peak leak from night_analysis notable_moments at clock " + best.path("clock").asText() + ".",
                List.of(new WindowEvidence("night_analysis", "peak_leak", (double) startSeconds,
                        best.path("timestamp").asText(null))),
                maxMinutes,
                null));
    }

    private Optional<WaveformWindowPlan> resolveNotableMoment(NightContext ctx, String anchorRequested, int maxMinutes) {
        if (ctx.nightAnalysis() == null) {
            return Optional.empty();
        }
        for (JsonNode moment : ctx.nightAnalysis().withArray("notable_moments")) {
            if (moment.path("nearby_events").isEmpty()) {
                continue;
            }
            OptionalLong offset = offsetSeconds(moment);
            if (offset.isEmpty()) {
                continue;
            }
            long startSeconds = offset.getAsLong();
            return Optional.of(buildPlan(
                    anchorRequested,
                    WaveformAnchorSupport.ANCHOR_NOTABLE_MOMENT,
                    startSeconds,
                    "First notable_moment with nearby_events on channel " + moment.path("channel").asText() + ".",
                    List.of(new WindowEvidence("night_analysis", moment.path("channel").asText(),
                            (double) startSeconds, moment.path("timestamp").asText(null))),
                    maxMinutes,
                    null));
        }
        return Optional.empty();
    }

    private WaveformWindowPlan planStageAnchor(String teamId, String date, String stageAnchor, int maxMinutes,
                                               Integer cpapClockAdjustSeconds) {
        String resolvedTeamId = requireTeamId(teamId);
        Optional<com.fasterxml.jackson.databind.node.ObjectNode> alignment =
                JournalSleepStageAlignment.align(resolvedTeamId, date, stageAnchor,
                        journalLookupService, waveformService, cpapClockAdjustSeconds);
        if (alignment.isEmpty()) {
            throw new IllegalArgumentException("no_stage_overlap: no " + stageAnchor
                    + " segment overlapping CPAP session for date " + date);
        }
        ObjectNode node = alignment.get();
        int startMinute = node.path("start_minute").asInt();
        int startSeconds = node.path("start_seconds").asInt();
        String confidence = node.path("alignment_confidence").asText("medium");
        return new WaveformWindowPlan(
                stageAnchor,
                stageAnchor,
                startMinute,
                startSeconds,
                WaveformAnchorSupport.LEAD_IN_MINUTES,
                maxMinutes,
                node.path("reason").asText(),
                List.of(new WindowEvidence("journal", stageAnchor, (double) startSeconds, null)),
                confidence);
    }

    private static OptionalLong offsetSeconds(JsonNode moment) {
        JsonNode offset = moment.get("offset_seconds");
        if (offset == null || !offset.isNumber() || offset.asLong() < 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(offset.asLong());
    }

    private static List<DeviceEvent> clinicalEveEvents(DeviceEventResult eve) {
        if (eve == null || eve.events() == null) {
            return List.of();
        }
        return eve.events().stream()
                .filter(e -> !WaveformAnchorSupport.isRecordingLabel(e.label()))
                .toList();
    }

    private static boolean isObstructiveEvent(DeviceEvent eve) {
        String label = eve.label() != null ? eve.label().toLowerCase(Locale.ROOT) : "";
        String code = eve.code() != null ? eve.code().toUpperCase(Locale.ROOT) : "";
        return label.contains("obstructive") || label.contains("hypopnea") || label.contains("flow")
                || code.equals("OA") || code.equals("H") || code.equals("FL");
    }

    private static boolean isCentralEvent(DeviceEvent eve) {
        String label = eve.label() != null ? eve.label().toLowerCase(Locale.ROOT) : "";
        String code = eve.code() != null ? eve.code().toUpperCase(Locale.ROOT) : "";
        return label.contains("central") || label.contains("clear airway") || code.equals("CA");
    }

    private static double obstructiveBonus(DeviceEvent eve) {
        return isObstructiveEvent(eve) ? 30.0 : 0.0;
    }

    private static WaveformWindowPlan buildPlan(String anchorRequested, String anchorResolved, double eventStartSeconds,
                                                String reason, List<WindowEvidence> evidence, int maxMinutes,
                                                String alignmentConfidence) {
        int startSeconds = WaveformAnchorSupport.startSecondsFromEventSeconds(eventStartSeconds);
        int startMinute = WaveformAnchorSupport.startMinuteFromEventSeconds(eventStartSeconds);
        return new WaveformWindowPlan(
                anchorRequested,
                anchorResolved,
                startMinute,
                startSeconds,
                WaveformAnchorSupport.LEAD_IN_MINUTES,
                maxMinutes,
                reason,
                evidence,
                alignmentConfidence);
    }

    private static WindowEvidence eveEvidence(DeviceEvent eve) {
        return new WindowEvidence("get-device-events", eve.label(), eve.startSeconds(), eve.timestamp());
    }

    private static WindowEvidence scanEvidence(ApneaEvent scan) {
        return new WindowEvidence("scan-apnea-events", scan.classification(), scan.startSeconds(), scan.timestamp());
    }

    private static String formatDuration(double seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }

    private String requireTeamId(String teamId) {
        if (teamId != null && !teamId.isBlank()) {
            return teamId;
        }
        String configured = clinical.defaultTeamId();
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Required teamId is missing and no default SLEEPHQ_TEAM_ID is configured");
        }
        return configured;
    }

    private record NightContext(DeviceEventResult eve, ApneaScanResult scan, ObjectNode nightAnalysis) {
    }

    private record OverlapPick(DeviceEvent eve, ApneaEvent scan, double score, List<WindowEvidence> evidence) {
    }
}
