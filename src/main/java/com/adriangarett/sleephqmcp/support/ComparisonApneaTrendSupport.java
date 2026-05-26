package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Computes multi-night obstructive (OA) and central (CA) apnea trends for {@code get-comparison}.
 */
public final class ComparisonApneaTrendSupport {

    private static final int TREND_WINDOW_NIGHTS = 7;
    private static final double RISING_RELATIVE_FACTOR = 1.25;
    private static final double RISING_MIN_ABSOLUTE_DELTA = 0.5;

    private ComparisonApneaTrendSupport() {
    }

    public static void attach(ObjectNode root, ArrayNode nights) {
        List<NightSample> samples = collectSamples(nights);
        ObjectNode trends = root.putObject("apnea_trends");
        trends.put("nights_in_span", nights.size());
        trends.put("nights_with_ahi_summary", samples.size());
        trends.put("oa_elevated_threshold_per_hr", AhiSummarySupport.OA_ELEVATED_PER_HR);
        trends.put("ca_elevated_threshold_per_hr", AhiSummarySupport.CA_ELEVATED_PER_HR);
        trends.put("note",
                "OA = obstructive apnea index (OSA residual on CPAP). CA = central apnea index (CSA/TECSA signal). "
                        + "Use apnea_trends before titrating pressure up (OA) or down (CA).");

        attachComponentTrend(trends, "ahi", samples, s -> s.components.ahiPerHr(), 1.0);
        attachComponentTrend(trends, "oa", samples, s -> optional(s.components.oaPerHr()), AhiSummarySupport.OA_ELEVATED_PER_HR);
        attachComponentTrend(trends, "ca", samples, s -> optional(s.components.caPerHr()), AhiSummarySupport.CA_ELEVATED_PER_HR);

        ArrayNode flagged = trends.putArray("nights_above_threshold");
        for (NightSample sample : samples) {
            AhiSummarySupport.Components c = sample.components;
            boolean oaFlag = AhiSummarySupport.isOaElevated(c.oaPerHr());
            boolean caFlag = AhiSummarySupport.isCaElevated(c.caPerHr());
            if (!oaFlag && !caFlag) {
                continue;
            }
            ObjectNode row = flagged.addObject();
            row.put("date", sample.date);
            row.put("ahi_per_hr", round(c.ahiPerHr()));
            if (c.oaPerHr() != null) {
                row.put("oa_per_hr", round(c.oaPerHr()));
            }
            if (c.caPerHr() != null) {
                row.put("ca_per_hr", round(c.caPerHr()));
            }
            if (c.hypopneaPerHr() != null) {
                row.put("h_per_hr", round(c.hypopneaPerHr()));
            }
            row.put("oa_elevated", oaFlag);
            row.put("ca_elevated", caFlag);
        }

        attachPressureSignals(trends, trends.path("oa"), trends.path("ca"));
        attachTitrationDecisionSupport(trends);
        attachDecisionGuardrails(root, trends);
    }

    private static void attachTitrationDecisionSupport(ObjectNode trends) {
        ObjectNode support = trends.putObject("titration_decision_support");
        support.put("for_workflow", "physician_titration_review");
        ArrayNode order = support.putArray("evaluate_in_order");
        order.add("1. Leak: leak_cell 95th ≥24 L/min or large_leak minutes → mask/seal **before** any pressure ↑");
        order.add("2. Usage: usage_cell <4 h on nights with CPAP data → compliance before interpreting AHI");
        order.add("3. Central (CSA): apnea_trends.ca.rising OR pressure_signals.possible_over_titration → **do not** ↑ pressure; consider −1 cmH₂O per handbook");
        order.add("4. Obstructive (OSA): apnea_trends.oa.rising OR possible_under_titration → may ↑ +1 if leak & usage OK");
        order.add("5. AHI total alone is not enough — use apnea_indices_cell (OSA · CSA · H · AHI); "
                + "AHI (av) includes hypopnea (h) and clear-airway (ca); ! = elevated OSA/CSA night");

        JsonNode signals = trends.path("pressure_signals");
        String action = "HOLD_PRESSURE";
        if (signals.path("possible_over_titration").asBoolean(false)) {
            action = "CONSIDER_DECREASE_1_OR_HOLD";
        } else if (signals.path("possible_under_titration").asBoolean(false)) {
            action = "CONSIDER_INCREASE_1_IF_LEAK_OK";
        }
        support.put("suggested_pressure_action", action);

        ArrayNode bullets = support.putArray("span_summary_bullets");
        appendTrendBullet(bullets, "OSA (OA)", trends.path("oa"));
        appendTrendBullet(bullets, "CSA (CA)", trends.path("ca"));
        appendTrendBullet(bullets, "AHI total", trends.path("ahi"));
        if (signals.path("possible_over_titration").asBoolean(false)) {
            bullets.add("Pressure signal: possible **over-titration** (rising CSA) — " + signals.path("over_titration_hint").asText(""));
        }
        if (signals.path("possible_under_titration").asBoolean(false)) {
            bullets.add("Pressure signal: possible **under-titration** (rising OSA) — " + signals.path("under_titration_hint").asText(""));
        }
    }

    private static void attachDecisionGuardrails(ObjectNode root, ObjectNode trends) {
        JsonNode caBlock = trends.path("ca");
        JsonNode oaBlock = trends.path("oa");
        JsonNode signals = trends.path("pressure_signals");

        boolean caAvailable = caBlock.path("available").asBoolean(false);
        boolean oaAvailable = oaBlock.path("available").asBoolean(false);
        boolean caRising = caBlock.path("rising").asBoolean(false);
        boolean oaRising = oaBlock.path("rising").asBoolean(false);
        boolean overTitration = signals.path("possible_over_titration").asBoolean(false);

        ObjectNode guardrails = root.putObject("decision_guardrails");
        guardrails.put("ca_status", caAvailable ? (caRising ? "rising" : "stable") : "insufficient_data");
        guardrails.put("oa_status", oaAvailable ? (oaRising ? "rising" : "stable") : "insufficient_data");

        boolean mustNotIncrease = caRising || overTitration;
        guardrails.put("must_not_increase_pressure", mustNotIncrease);
        guardrails.put("must_not_increase_reason",
                mustNotIncrease ? buildMustNotIncreaseReason(caBlock, signals, caRising) : "");
        guardrails.put("mask_fit_check_required", false);
        guardrails.put("mask_fit_reason",
                "Verify mask fit from nights[].table_display.leak_cell (95th ≥24 L/min = mask first). "
                        + "F40 with Pillows device menu is valid per ResMed; recommend change only with measured leak evidence.");
    }

    private static String buildMustNotIncreaseReason(JsonNode caBlock, JsonNode signals, boolean caRising) {
        if (caRising) {
            double recent = caBlock.path("recent_7d_mean_per_hr").asDouble(Double.NaN);
            double prior = caBlock.path("prior_7d_mean_per_hr").asDouble(Double.NaN);
            if (!Double.isNaN(recent) && !Double.isNaN(prior)) {
                return String.format(
                        "CA is rising (recent %.2f/hr vs prior %.2f/hr) — increasing CPAP pressure may worsen central apnea (TECSA risk)",
                        recent, prior);
            }
            return "CA index is rising — increasing CPAP pressure may worsen central apnea (TECSA risk)";
        }
        return signals.path("over_titration_hint").asText(
                "Possible over-titration — hold or consider decrease before any pressure increase");
    }

    private static void appendTrendBullet(ArrayNode bullets, String label, JsonNode block) {
        if (!block.path("available").asBoolean(false)) {
            bullets.add(label + ": no data in span");
            return;
        }
        StringBuilder line = new StringBuilder(label).append(": mean ").append(block.path("mean_per_hr").asDouble());
        line.append("/hr");
        if (block.path("rising").asBoolean(false)) {
            line.append(", **rising** (").append(block.path("rising_hint").asText("")).append(')');
        }
        if (block.path("elevated_nights_count").asInt() > 0) {
            line.append(", ").append(block.path("elevated_nights_count").asInt()).append(" elevated night(s)");
        }
        bullets.add(line.toString());
    }

    private static double optional(Double value) {
        return value != null ? value : Double.NaN;
    }

    private static void attachComponentTrend(ObjectNode trends, String key, List<NightSample> samples,
                                             ToDoubleFunction<NightSample> extractor, double elevatedThreshold) {
        List<NightSample> withValue = samples.stream()
                .filter(s -> !Double.isNaN(extractor.applyAsDouble(s)))
                .toList();
        ObjectNode block = trends.putObject(key);
        if (withValue.isEmpty()) {
            block.put("available", false);
            return;
        }
        block.put("available", true);
        double sum = 0;
        double max = Double.NEGATIVE_INFINITY;
        String maxDate = null;
        int elevated = 0;
        double threshold = elevatedThreshold;

        for (NightSample sample : withValue) {
            double v = extractor.applyAsDouble(sample);
            sum += v;
            if (v > max) {
                max = v;
                maxDate = sample.date;
            }
            if (v >= threshold) {
                elevated++;
            }
        }
        int n = withValue.size();
        block.put("mean_per_hr", round(sum / n));
        block.put("max_per_hr", round(max));
        if (maxDate != null) {
            block.put("max_date", maxDate);
        }
        block.put("elevated_nights_count", elevated);
        block.put("elevated_threshold_per_hr", threshold);

        WindowSplit split = splitRecentPrior(withValue, extractor);
        if (split.recentCount() > 0) {
            block.put("recent_7d_mean_per_hr", round(split.recentMean()));
            block.put("recent_7d_nights", split.recentCount());
        }
        if (split.priorCount() > 0) {
            block.put("prior_7d_mean_per_hr", round(split.priorMean()));
            block.put("prior_7d_nights", split.priorCount());
        }
        boolean rising = isRising(split, elevatedThreshold);
        block.put("rising", rising);
        if (rising) {
            block.put("rising_hint", buildRisingHint(key, split));
        }
    }

    private static void attachPressureSignals(ObjectNode trends, JsonNode oaBlock, JsonNode caBlock) {
        ObjectNode signals = trends.putObject("pressure_signals");
        boolean oaRising = oaBlock.path("rising").asBoolean(false);
        boolean caRising = caBlock.path("rising").asBoolean(false);
        double oaRecent = oaBlock.path("recent_7d_mean_per_hr").asDouble(Double.NaN);
        double caRecent = caBlock.path("recent_7d_mean_per_hr").asDouble(Double.NaN);

        boolean overTitration = caRising && !Double.isNaN(caRecent) && caRecent >= AhiSummarySupport.CA_ELEVATED_PER_HR
                && (Double.isNaN(oaRecent) || oaRecent < AhiSummarySupport.OA_ELEVATED_PER_HR);
        boolean underTitration = oaRising && !Double.isNaN(oaRecent) && oaRecent >= AhiSummarySupport.OA_ELEVATED_PER_HR
                && (Double.isNaN(caRecent) || caRecent < AhiSummarySupport.CA_ELEVATED_PER_HR);

        signals.put("possible_over_titration", overTitration);
        signals.put("possible_under_titration", underTitration);
        if (overTitration) {
            signals.put("over_titration_hint",
                    "Rising CA with CA ≥ " + AhiSummarySupport.CA_ELEVATED_PER_HR
                            + "/hr — consider pressure decrease before increase (TECSA/central branch).");
        }
        if (underTitration) {
            signals.put("under_titration_hint",
                    "Rising OA with elevated obstructive index — residual OSA; do not lower pressure for central pattern.");
        }
    }

    private static boolean isRising(WindowSplit split, double elevatedThreshold) {
        if (split.recentCount() == 0) {
            return false;
        }
        if (split.priorCount() == 0) {
            return split.recentMean() >= elevatedThreshold;
        }
        double delta = split.recentMean() - split.priorMean();
        return delta >= RISING_MIN_ABSOLUTE_DELTA
                && split.recentMean() >= split.priorMean() * RISING_RELATIVE_FACTOR;
    }

    private static String buildRisingHint(String key, WindowSplit split) {
        String label = "ca".equals(key) ? "CSA (CA index)" : "oa".equals(key) ? "OSA residual (OA index)" : "AHI";
        if (split.priorCount() == 0) {
            return label + " recent mean " + round(split.recentMean()) + "/hr (no prior window)";
        }
        return label + " recent " + round(split.recentMean()) + "/hr vs prior " + round(split.priorMean()) + "/hr";
    }

    private static WindowSplit splitRecentPrior(List<NightSample> chronological,
                                                ToDoubleFunction<NightSample> extractor) {
        int n = chronological.size();
        int window = Math.min(TREND_WINDOW_NIGHTS, n / 2);
        if (window == 0) {
            window = Math.min(TREND_WINDOW_NIGHTS, n);
        }
        if (window == 0) {
            return new WindowSplit(0, 0, Double.NaN, Double.NaN);
        }
        int priorEnd = n - window;
        int priorStart = Math.max(0, priorEnd - window);
        double recentSum = 0;
        int recentCount = 0;
        for (int i = priorEnd; i < n; i++) {
            recentSum += extractor.applyAsDouble(chronological.get(i));
            recentCount++;
        }
        double priorSum = 0;
        int priorCount = 0;
        for (int i = priorStart; i < priorEnd; i++) {
            priorSum += extractor.applyAsDouble(chronological.get(i));
            priorCount++;
        }
        double recentMean = recentCount > 0 ? recentSum / recentCount : Double.NaN;
        double priorMean = priorCount > 0 ? priorSum / priorCount : Double.NaN;
        return new WindowSplit(recentCount, priorCount, recentMean, priorMean);
    }

    private static List<NightSample> collectSamples(ArrayNode nights) {
        List<NightSample> samples = new ArrayList<>();
        for (JsonNode node : nights) {
            if (!node.isObject()) {
                continue;
            }
            String date = node.path("date").asText(null);
            if (date == null || date.isBlank()) {
                continue;
            }
            AhiSummarySupport.readFromNightRow(node)
                    .ifPresent(components -> samples.add(new NightSample(date, components)));
        }
        samples.sort(Comparator.comparing(s -> s.date));
        return samples;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record NightSample(String date, AhiSummarySupport.Components components) {
    }

    private record WindowSplit(int recentCount, int priorCount, double recentMean, double priorMean) {
    }
}
