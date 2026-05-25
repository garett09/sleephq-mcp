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

    public static ObjectNode legendNode() {
        ObjectNode legend = JsonApi.mapper().createObjectNode();
        LEGEND.forEach((code, label) -> legend.put(String.valueOf(code), label));
        return legend;
    }
}
