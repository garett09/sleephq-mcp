package com.adriangarett.sleephqmcp.support;

import java.util.Locale;
import java.util.Set;

public final class WaveformAnchorSupport {

    public static final int LEAD_IN_MINUTES = 5;
    public static final int EVE_SCAN_OVERLAP_SECONDS = 120;
    public static final int SECOND_WINDOW_MIN_GAP_MINUTES = 30;

    public static final String ANCHOR_AUTO = "auto";
    public static final String ANCHOR_MANUAL = "manual";
    public static final String ANCHOR_EVE_SCAN_OVERLAP = "eve_scan_overlap";
    public static final String ANCHOR_WORST_OBSTRUCTIVE = "worst_obstructive";
    public static final String ANCHOR_WORST_CENTRAL = "worst_central";
    public static final String ANCHOR_WORST_LEAK = "worst_leak";
    public static final String ANCHOR_NOTABLE_MOMENT = "notable_moment";
    public static final String ANCHOR_REM = "rem";
    public static final String ANCHOR_DEEP = "deep";
    public static final String ANCHOR_CORE = "core";
    public static final String ANCHOR_AWAKE = "awake";

    public static final Set<String> V1_EVENT_ANCHORS = Set.of(
            ANCHOR_AUTO,
            ANCHOR_EVE_SCAN_OVERLAP,
            ANCHOR_WORST_OBSTRUCTIVE,
            ANCHOR_WORST_CENTRAL,
            ANCHOR_WORST_LEAK,
            ANCHOR_NOTABLE_MOMENT);

    public static final Set<String> STAGE_ANCHORS = Set.of(
            ANCHOR_REM,
            ANCHOR_DEEP,
            ANCHOR_CORE,
            ANCHOR_AWAKE);

    private WaveformAnchorSupport() {
    }

    public static boolean isRecordingLabel(String label) {
        return label != null && label.toLowerCase(Locale.ROOT).contains("recording");
    }

    public static long parseClockToSeconds(String clock) {
        if (clock == null || clock.isBlank()) {
            return 0L;
        }
        String[] parts = clock.split(":");
        if (parts.length != 3) {
            return 0L;
        }
        return Long.parseLong(parts[0]) * 3600L
                + Long.parseLong(parts[1]) * 60L
                + Long.parseLong(parts[2]);
    }

    public static int startMinuteFromEventSeconds(double eventStartSeconds) {
        int leadInSeconds = LEAD_IN_MINUTES * 60;
        int startSeconds = (int) Math.max(0, Math.floor(eventStartSeconds) - leadInSeconds);
        return startSeconds / 60;
    }

    public static int startSecondsFromEventSeconds(double eventStartSeconds) {
        int leadInSeconds = LEAD_IN_MINUTES * 60;
        return (int) Math.max(0, Math.floor(eventStartSeconds) - leadInSeconds);
    }
}
