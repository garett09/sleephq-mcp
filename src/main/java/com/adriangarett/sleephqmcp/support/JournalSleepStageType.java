package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Maps journal {@code stage_type} integers (Apple Health sleep analysis via SleepHQ) to stable labels.
 */
public final class JournalSleepStageType {

    /** SleepHQ / Apple Health {@code HKCategoryValueSleepAnalysis} style values. */
    public static final Map<Integer, String> LEGEND = Map.of(
            0, "in_bed",
            1, "asleep_unspecified",
            2, "awake",
            3, "core",
            4, "deep",
            5, "rem");

    private JournalSleepStageType() {
    }

    public static String labelFor(int stageType) {
        return LEGEND.getOrDefault(stageType, "unknown_" + stageType);
    }

    /**
     * When segments overlap in time, the higher priority label wins for that interval (awake > deep > rem > core).
     */
    public static int timelinePriority(String label) {
        return switch (label) {
            case "awake" -> 100;
            case "deep" -> 80;
            case "rem" -> 60;
            case "core" -> 40;
            case "asleep_unspecified" -> 20;
            case "in_bed" -> 10;
            default -> 5;
        };
    }

    public static ObjectNode legendNode() {
        ObjectNode legend = JsonApi.mapper().createObjectNode();
        LEGEND.forEach((code, label) -> legend.put(String.valueOf(code), label));
        return legend;
    }
}
