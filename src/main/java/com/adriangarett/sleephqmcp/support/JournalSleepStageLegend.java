package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SleepHQ journal {@code stage_type} integers are usually Apple Health HK values, but some accounts
 * store SleepHQ dashboard encoding (2=deep, 4=rem, 5=awake). Auto-detect from segment totals.
 */
public enum JournalSleepStageLegend {

    APPLE_HEALTH("apple_health") {
        @Override
        public String labelFor(int stageType) {
            return JournalSleepStageType.labelFor(stageType);
        }

        @Override
        public ObjectNode legendNode() {
            return JournalSleepStageType.legendNode();
        }
    },
    SLEEPHQ_DASHBOARD("sleephq_dashboard") {
        @Override
        public String labelFor(int stageType) {
            return switch (stageType) {
                case 0 -> "in_bed";
                case 1 -> "asleep_unspecified";
                case 2 -> "deep";
                case 3 -> "core";
                case 4 -> "rem";
                case 5 -> "awake";
                default -> "unknown_" + stageType;
            };
        }

        @Override
        public ObjectNode legendNode() {
            ObjectNode legend = JsonApi.mapper().createObjectNode();
            legend.put("0", "in_bed");
            legend.put("1", "asleep_unspecified");
            legend.put("2", "deep");
            legend.put("3", "core");
            legend.put("4", "rem");
            legend.put("5", "awake");
            legend.put("note", "SleepHQ dashboard encoding (not Apple Health HK)");
            return legend;
        }
    };

    private final String profileId;

    JournalSleepStageLegend(String profileId) {
        this.profileId = profileId;
    }

    public String profileId() {
        return profileId;
    }

    public abstract String labelFor(int stageType);

    public abstract ObjectNode legendNode();

    /**
     * Prefer {@link #SLEEPHQ_DASHBOARD} when Apple Health mapping yields implausible awake vs REM totals
     * but dashboard mapping yields REM-rich sleep typical of SleepHQ UI cards.
     */
    public static JournalSleepStageLegend detect(Map<String, Long> hkNaiveSeconds, Map<String, Long> shqNaiveSeconds) {
        double hkRemMin = minutes(hkNaiveSeconds, "rem");
        double hkAwakeMin = minutes(hkNaiveSeconds, "awake");
        double shqRemMin = minutes(shqNaiveSeconds, "rem");
        double shqAwakeMin = minutes(shqNaiveSeconds, "awake");

        // Same segments, two legends: dashboard encoding shifts REM↑ and awake↓ vs Apple Health.
        boolean shqMatchesDashboardShape = shqRemMin > hkRemMin + 60.0 && shqAwakeMin + 60.0 < hkAwakeMin;
        boolean hkAwakeHeavy = hkAwakeMin > hkRemMin + 30.0 && hkAwakeMin > 90.0;
        boolean shqRemRich = shqRemMin > 90.0 && shqAwakeMin < 90.0;
        if (shqMatchesDashboardShape || (hkAwakeHeavy && shqRemRich)) {
            return SLEEPHQ_DASHBOARD;
        }
        return APPLE_HEALTH;
    }

    private static double minutes(Map<String, Long> byLabel, String label) {
        return byLabel.getOrDefault(label, 0L) / 60.0;
    }

    public static Map<String, Long> naiveSecondsByLabel(Iterable<StageInterval> intervals, JournalSleepStageLegend legend) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StageInterval interval : intervals) {
            String label = legend.labelFor(interval.stageType());
            out.merge(label, interval.durationSeconds(), Long::sum);
        }
        return out;
    }

    public record StageInterval(int stageType, long durationSeconds) {
    }
}
