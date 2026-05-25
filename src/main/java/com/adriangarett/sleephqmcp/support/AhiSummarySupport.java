package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Reads therapy apnea indices from SleepHQ {@code machine_date.attributes.ahi_summary}.
 */
public final class AhiSummarySupport {

    /** Residual obstructive concern on CPAP (aligns with nightly-review AHI gate). */
    public static final double OA_ELEVATED_PER_HR = 1.0;
    /** Central apnea index — {@code sleephq://reference/normal-ranges}. */
    public static final double CA_ELEVATED_PER_HR = 5.0;

    private static final String[] AVG_KEYS = {"av", "avg", "average"};

    private AhiSummarySupport() {
    }

    public record Components(double ahiPerHr, Double oaPerHr, Double caPerHr, Double hypopneaPerHr) {
    }

    public static Optional<Components> readComponents(JsonNode ahiSummary) {
        if (ahiSummary == null || !ahiSummary.isObject() || ahiSummary.isEmpty()) {
            return Optional.empty();
        }
        Double ahi = readNumeric(ahiSummary, AVG_KEYS);
        if (ahi == null) {
            return Optional.empty();
        }
        return Optional.of(new Components(
                ahi,
                readNumeric(ahiSummary, "oa"),
                readNumeric(ahiSummary, "ca"),
                readNumeric(ahiSummary, "h")));
    }

    public static Optional<Components> readFromNightRow(JsonNode nightRow) {
        if (nightRow == null || nightRow.path("skipped").asBoolean(false)) {
            return Optional.empty();
        }
        JsonNode attrs = nightRow.path("data").path("attributes");
        if (!attrs.isObject()) {
            return Optional.empty();
        }
        return readComponents(attrs.path("ahi_summary"));
    }

    public static boolean isOaElevated(Double oaPerHr) {
        return oaPerHr != null && oaPerHr >= OA_ELEVATED_PER_HR;
    }

    public static boolean isCaElevated(Double caPerHr) {
        return caPerHr != null && caPerHr >= CA_ELEVATED_PER_HR;
    }

    private static Double readNumeric(JsonNode summary, String... keys) {
        for (String key : keys) {
            JsonNode node = summary.path(key);
            if (node.isNumber()) {
                return node.asDouble();
            }
        }
        return null;
    }
}
