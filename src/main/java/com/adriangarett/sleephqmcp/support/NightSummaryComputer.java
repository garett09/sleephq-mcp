package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.NightChannelSummary;
import com.adriangarett.sleephqmcp.oscar.OscarChannelUnitNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoublePredicate;

/**
 * Builds a {@link NightChannelSummary} from raw channel samples: applies the display-unit conversion,
 * computes p99/p95/median/min/max/avg via {@link ChannelPercentiles}, and attaches clinical markers.
 * Percentiles use <strong>every</strong> finite sample after unit scaling (NaN/null only) — same
 * ceil-rank distribution as OSCAR / SleepHQ; no therapy or large-leak subsets.
 * Thresholds for markers only are named constants aligned with {@code sleephq://reference/normal-ranges}.
 */
public final class NightSummaryComputer {

    public static final double LEAK_LARGE_THRESHOLD_L_MIN = 24.0;
    public static final double SPO2_DESAT_THRESHOLD = 88.0;
    private static final double AT_MAX_EPSILON = 0.05;
    private NightSummaryComputer() {}

    /** Maps a PLD EDF channel label to a tool field, or null if not summarised. */
    public static String mapPldLabel(String label) {
        if (label == null) {
            return null;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.startsWith("maskpress")) return "mask_pressure";
        if (lower.startsWith("eprpress")) return "epap";
        if (lower.startsWith("press")) return "pressure";
        if (lower.startsWith("leak")) return "leak_rate";
        if (lower.startsWith("resprate")) return "resp_rate";
        if (lower.startsWith("tidvol")) return "tidal_volume";
        if (lower.startsWith("minvent")) return "minute_vent";
        if (lower.startsWith("snore")) return "snore";
        if (lower.startsWith("flowlim")) return "flow_limit";
        return null;
    }

    /** @return the summary, or null if no valid samples. */
    public static NightChannelSummary summarise(String field, String rawUnit, List<Double> rawSamples, double sampleRate) {
        return summarise(field, rawUnit, null, rawSamples, sampleRate);
    }

    /** @param edfLabel PLD/Viatom channel label (e.g. {@code Leak.2s}) for unit inference when EDF unit is blank. */
    public static NightChannelSummary summarise(String field, String rawUnit, String edfLabel,
                                                List<Double> rawSamples, double sampleRate) {
        OscarChannelUnitNormalizer.UnitConversion conv =
                OscarChannelUnitNormalizer.conversionFor(field, rawUnit, rawSamples, edfLabel);
        List<Double> scaled = new ArrayList<>(rawSamples.size());
        for (Double v : rawSamples) {
            if (v != null && !Double.isNaN(v)) {
                scaled.add(v * conv.factor());
            }
        }
        if (scaled.isEmpty()) {
            return null;
        }
        List<Double> sorted = ChannelPercentiles.sortedClean(scaled);
        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        Map<String, Double> markers = markersFor(field, scaled, sampleRate, min, max);
        return new NightChannelSummary(
                conv.unit(),
                round(ChannelPercentiles.percentile(sorted, 99)),
                round(ChannelPercentiles.percentile(sorted, 99.5)),
                round(ChannelPercentiles.percentile(sorted, 95)),
                round(ChannelPercentiles.percentile(sorted, 50)),
                round(min),
                round(max),
                round(ChannelPercentiles.avg(sorted)),
                sorted.size(),
                markers.isEmpty() ? null : markers);
    }

    private static Map<String, Double> markersFor(String field, List<Double> values, double sampleRate,
                                                  double min, double max) {
        Map<String, Double> m = new LinkedHashMap<>();
        double interval = sampleRate > 0 ? 1.0 / sampleRate : 0.0;
        int total = values.size();
        switch (field) {
            case "leak_rate" -> {
                int above = countWhere(values, v -> v > LEAK_LARGE_THRESHOLD_L_MIN);
                m.put("time_above_24_l_min_seconds", round(above * interval));
                m.put("time_above_24_l_min_pct", round(pct(above, total)));
            }
            case "spo2" -> {
                int below = countWhere(values, v -> v < SPO2_DESAT_THRESHOLD);
                m.put("nadir", round(min));
                m.put("time_below_88_pct_seconds", round(below * interval));
                m.put("time_below_88_pct", round(pct(below, total)));
            }
            case "pressure" -> {
                int atMax = countWhere(values, v -> v >= max - AT_MAX_EPSILON);
                m.put("max_pressure", round(max));
                m.put("time_at_max_seconds", round(atMax * interval));
                m.put("time_at_max_pct", round(pct(atMax, total)));
            }
            default -> { /* no markers */ }
        }
        return m;
    }

    private static int countWhere(List<Double> values, DoublePredicate p) {
        int n = 0;
        for (double v : values) {
            if (p.test(v)) {
                n++;
            }
        }
        return n;
    }

    private static double pct(int n, int total) {
        return total == 0 ? 0.0 : (double) n / total * 100.0;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
