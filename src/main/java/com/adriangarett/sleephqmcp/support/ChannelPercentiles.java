package com.adriangarett.sleephqmcp.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure distribution math shared by OSCAR ({@code OscarWaveformStatistics}) and the SleepHQ night
 * summary ({@code NightSummaryComputer}) so the two paths cannot drift. Ceil-rank percentile,
 * matching what ResMed/SleepHQ report as {@code .50}/{@code upper}.
 */
public final class ChannelPercentiles {

    private ChannelPercentiles() {}

    /** NaN-filtered, ascending-sorted copy of {@code samples}. */
    public static List<Double> sortedClean(List<Double> samples) {
        List<Double> out = new ArrayList<>(samples.size());
        for (Double v : samples) {
            if (v != null && !Double.isNaN(v)) {
                out.add(v);
            }
        }
        out.sort(Double::compareTo);
        return out;
    }

    /** Ceil-rank percentile over an ascending-sorted list. Returns 0 for an empty list. */
    public static double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    /** Mean of the values. Returns 0 for an empty list. */
    public static double avg(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }
}
