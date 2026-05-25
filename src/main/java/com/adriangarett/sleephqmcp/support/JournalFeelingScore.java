package com.adriangarett.sleephqmcp.support;

import java.util.Map;
import java.util.Optional;

/**
 * Maps SleepHQ journal {@code feeling_score} (1–5, lowest to highest) to the mood labels shown in the SleepHQ UI.
 * The API returns only the integer; labels match the in-app journal emoji scale.
 */
public final class JournalFeelingScore {

    /** SleepHQ journal mood scale (1 = worst, 5 = best). */
    public static final Map<Integer, String> LABELS = Map.of(
            1, "Awful",
            2, "Poor",
            3, "Okay",
            4, "Good",
            5, "Great");

    private JournalFeelingScore() {
    }

    public static Optional<String> labelFor(int score) {
        return Optional.ofNullable(LABELS.get(score));
    }

    /**
     * User-facing text for tables and narrative (e.g. {@code Okay (3)}).
     */
    public static String displayFor(int score) {
        return labelFor(score)
                .map(label -> label + " (" + score + ")")
                .orElse("feeling " + score);
    }
}
