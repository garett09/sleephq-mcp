package com.adriangarett.sleephqmcp.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Maps OSCAR export recency (newest session in the local index vs today) to freshness categories.
 * This is export-wide, not per analyzed calendar night.
 */
public final class OscarFreshness {

    public static final String SCOPE_EXPORT = "export";

    private OscarFreshness() {
    }

    public static long exportLagDays(LocalDate lastSessionDate, LocalDate today) {
        return Math.max(0L, ChronoUnit.DAYS.between(lastSessionDate, today));
    }

    public static String categoryFromExportLagDays(long lagDays) {
        if (lagDays <= 0) {
            return "fresh";
        }
        if (lagDays <= 6) {
            return "acceptable_lag";
        }
        if (lagDays <= 29) {
            return "stale";
        }
        return "very_stale";
    }
}
