package com.adriangarett.sleephqmcp.oscar;

import java.util.Locale;
import java.util.Set;

/**
 * Maps raw EVE.edf annotation labels to the canonical field names used in
 * summary counts (e.g. {@code "obstructive"}, {@code "clear_airway"}).
 * This lets downstream code compare {@code events.counts} (from EVE annotations)
 * directly against {@code events.summary_counts} (from the SQLite summary).
 *
 * <p>Pure utility — no Spring bean, no state.</p>
 */
public final class OscarEventLabelCanonicalizer {

    /** Labels that represent recording lifecycle markers, not therapy events. */
    private static final Set<String> NON_THERAPY_EXACT = Set.of(
            "starting", "stopping", "start", "stop", "unknown"
    );

    private OscarEventLabelCanonicalizer() {}

    /**
     * Returns the canonical summary field name for {@code raw}, or {@code null}
     * if the label is blank, null, or a non-therapy recording marker.
     *
     * <p>Matching is case-insensitive.  The order of guards matters — more
     * specific phrases are checked before shorter ones (e.g. "obstructive apnea"
     * before bare "apnea").</p>
     */
    public static String canonical(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String lower = raw.toLowerCase(Locale.ROOT);

        // Recording lifecycle markers → null
        if (lower.startsWith("recording")) {
            return null;
        }
        if (NON_THERAPY_EXACT.contains(lower)) {
            return null;
        }

        // Obstructive apnea — must check before bare "apnea"
        if (lower.contains("obstructive") && lower.contains("apnea")) {
            return "obstructive";
        }

        // Central / clear airway
        if (lower.contains("central")
                || lower.contains("clear airway")
                || lower.contains("clear_airway")) {
            return "clear_airway";
        }

        // Hypopnea — before bare "apnea"
        if (lower.contains("hypopnea")) {
            return "hypopnea";
        }

        // Flow limit (multiple spellings)
        if (lower.contains("flow limit")
                || lower.contains("flow_limit")
                || lower.contains("flow limitation")) {
            return "flow_limit_events";
        }

        // Large leak
        if (lower.contains("large leak") || lower.contains("large_leak")) {
            return "large_leak";
        }

        // RERA / arousal
        if (lower.contains("rera") || lower.contains("arousal")) {
            return "rera";
        }

        // Vibratory snore
        if (lower.contains("vibratory") || lower.contains("snore")) {
            return "vibratory_snore";
        }

        // Pressure pulse
        if (lower.contains("pressure pulse") || lower.contains("pressure_pulse")) {
            return "pressure_pulse";
        }

        // Cheyne-Stokes respiration
        if (lower.contains("cheyne")) {
            return "csr";
        }

        // Bare apnea (after all compound apnea guards)
        if (lower.equals("apnea")) {
            return "apnea";
        }

        // Fallback: normalize to snake_case
        return lower.replaceAll("[\\s]+", "_");
    }

    /**
     * Returns {@code true} when {@code raw} is a recording lifecycle marker
     * (or blank/null) rather than a therapy event.
     */
    public static boolean isNonTherapy(String raw) {
        return canonical(raw) == null;
    }
}
