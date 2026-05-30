package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.NightChannelSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Self-validation: compares computed p95/median to SleepHQ's own per-night {@code machine_date}
 * summaries ({@code {upper=p95, med=median}}). {@code agree} is a sanity band (relative ±10% or
 * absolute ±1.0), NOT a defect proof — SleepHQ/ResMed grouping may differ. Returns null when no
 * channel has a corresponding summary.
 */
public final class NightSummaryValidation {

    private static final double REL_TOL = 0.10;
    private static final double ABS_TOL = 1.0;

    private static final Map<String, String> CPAP_FIELDS = Map.of(
            "pressure", "pressure_summary",
            "leak_rate", "leak_rate_summary",
            "resp_rate", "resp_rate_summary",
            "flow_limit", "flow_limit_summary",
            "epap", "epap_summary");
    private static final Map<String, String> O2_FIELDS = Map.of(
            "spo2", "spo2_summary",
            "pulse_rate", "pulse_rate_summary");

    private NightSummaryValidation() {}

    public static ObjectNode build(Map<String, NightChannelSummary> channels, JsonNode cpapAttrs, JsonNode o2Attrs) {
        ObjectNode out = JsonApi.mapper().createObjectNode();
        addEntries(out, channels, cpapAttrs, CPAP_FIELDS);
        addEntries(out, channels, o2Attrs, O2_FIELDS);
        return out.isEmpty() ? null : out;
    }

    private static void addEntries(ObjectNode out, Map<String, NightChannelSummary> channels,
                                   JsonNode attrs, Map<String, String> fieldToSummary) {
        if (attrs == null || !attrs.isObject()) {
            return;
        }
        for (Map.Entry<String, String> e : fieldToSummary.entrySet()) {
            NightChannelSummary ch = channels.get(e.getKey());
            if (ch == null) {
                continue;
            }
            JsonNode summary = attrs.path(e.getValue());
            if (!summary.isObject() || summary.isEmpty()) {
                continue;
            }
            Double sleepHqP95 = AhiSummarySupport.readNumeric(summary, "upper");
            Double sleepHqMedian = AhiSummarySupport.readNumeric(summary, "med");
            if (sleepHqP95 == null && sleepHqMedian == null) {
                continue;
            }
            ObjectNode entry = out.putObject(e.getKey());
            entry.put("our_p95", ch.p95());
            if (sleepHqP95 != null) {
                entry.put("sleephq_p95", sleepHqP95);
            }
            entry.put("our_median", ch.median());
            if (sleepHqMedian != null) {
                entry.put("sleephq_median", sleepHqMedian);
            }
            boolean p95Ok = sleepHqP95 == null || within(ch.p95(), sleepHqP95);
            boolean medOk = sleepHqMedian == null || within(ch.median(), sleepHqMedian);
            entry.put("agree", p95Ok && medOk);
        }
    }

    private static boolean within(double ours, double theirs) {
        double tol = Math.max(ABS_TOL, REL_TOL * Math.abs(theirs));
        return Math.abs(ours - theirs) <= tol;
    }
}
