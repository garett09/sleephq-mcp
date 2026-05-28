package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Agent-friendly therapy summaries on single-night envelopes ({@code get-combined-night-by-date},
 * {@code get-night-stats}).
 */
public final class NightTherapyDisplaySupport {

    private NightTherapyDisplaySupport() {
    }

    /**
     * Adds {@code ahi_components} and {@code therapy_display} when {@code data.attributes.ahi_summary} exists.
     */
    public static void attachIfPresent(ObjectNode envelope) {
        JsonNode data = envelope.path("data");
        if (!data.isObject()) {
            return;
        }
        JsonNode attrs = data.path("attributes");
        if (!attrs.isObject()) {
            return;
        }
        JsonNode ahiSummary = attrs.path("ahi_summary");
        if (!ahiSummary.isObject() || ahiSummary.isEmpty()) {
            return;
        }
        attachAhiComponents(envelope, ahiSummary);
        attachTherapyDisplay(envelope, ahiSummary, attrs);
    }

    private static void attachAhiComponents(ObjectNode envelope, JsonNode ahiSummary) {
        AhiSummarySupport.readComponents(ahiSummary).ifPresent(components -> {
            ObjectNode out = envelope.putObject("ahi_components");
            out.put("ahi_per_hr", components.ahiPerHr());
            if (components.oaPerHr() != null) {
                out.put("oa_per_hr", components.oaPerHr());
                out.put("osa_elevated", AhiSummarySupport.isOaElevated(components.oaPerHr()));
            }
            if (components.caPerHr() != null) {
                out.put("ca_per_hr", components.caPerHr());
                out.put("csa_elevated", AhiSummarySupport.isCaElevated(components.caPerHr()));
            }
            if (components.hypopneaPerHr() != null) {
                out.put("h_per_hr", components.hypopneaPerHr());
            }
        });
    }

    private static void attachTherapyDisplay(ObjectNode envelope, JsonNode ahiSummary, JsonNode attrs) {
        ObjectNode display = envelope.putObject("therapy_display");
        String indicesCell = ComparisonTableDisplay.buildApneaIndicesCell(ahiSummary);
        if (!indicesCell.isBlank()) {
            display.put("apnea_indices_cell", indicesCell);
        }
        AhiSummarySupport.readComponents(ahiSummary).ifPresent(components -> {
            ObjectNode apnea = display.putObject("apnea");
            apnea.put("ahi_per_hr", components.ahiPerHr());
            if (components.oaPerHr() != null) {
                apnea.put("osa_per_hr", components.oaPerHr());
                apnea.put("osa_elevated", AhiSummarySupport.isOaElevated(components.oaPerHr()));
            }
            if (components.caPerHr() != null) {
                apnea.put("csa_per_hr", components.caPerHr());
                apnea.put("csa_elevated", AhiSummarySupport.isCaElevated(components.caPerHr()));
            }
            if (components.hypopneaPerHr() != null) {
                apnea.put("hypopnea_per_hr", components.hypopneaPerHr());
            }
        });
        String usageCell = ComparisonTableDisplay.buildUsageCellFromAttributes(attrs);
        if (!usageCell.isBlank()) {
            display.put("usage_cell", usageCell);
        }
        String leakCell = ComparisonTableDisplay.buildLeakCellFromAttributes(attrs);
        if (!leakCell.isBlank()) {
            display.put("leak_cell", leakCell);
        }
        display.put("cpap_section_hint",
                "In user-facing CPAP Therapy summaries, list obstructive (OSA), central (CSA), hypopneas (H), "
                        + "and total AHI — use therapy_display.apnea or apnea_indices_cell; do not show total AHI "
                        + "alone when OSA/CSA/H are present.");
    }
}
