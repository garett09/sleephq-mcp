package com.adriangarett.sleephqmcp.oscar;

import java.util.TreeMap;

public final class OscarHistogram {

    private OscarHistogram() {}

    public static TreeMap<Integer, Long> merge(TreeMap<Integer, Long> a, TreeMap<Integer, Long> b) {
        TreeMap<Integer, Long> result = new TreeMap<>(a);
        b.forEach((value, count) -> result.merge(value, count, Long::sum));
        return result;
    }

    /**
     * Computes pct-th percentile (0–100) from an integer value→count histogram.
     * Physical value = raw_value * gainFactor.
     * Returns NaN if the histogram is empty.
     */
    public static double percentile(TreeMap<Integer, Long> histogram, double gainFactor, double pct) {
        if (histogram.isEmpty()) return Double.NaN;
        long total = histogram.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return Double.NaN;
        long target = (long) Math.ceil(pct / 100.0 * total);
        long cumulative = 0;
        for (var entry : histogram.entrySet()) {
            cumulative += entry.getValue();
            if (cumulative >= target) {
                return Math.round(entry.getKey() * gainFactor * 100.0) / 100.0;
            }
        }
        return Math.round(histogram.lastKey() * gainFactor * 100.0) / 100.0;
    }

    /** Weighted average: sum(value * count) / sum(count) * gainFactor. */
    public static double avg(TreeMap<Integer, Long> histogram, double gainFactor) {
        if (histogram.isEmpty()) return Double.NaN;
        long totalCount = 0;
        double weightedSum = 0;
        for (var entry : histogram.entrySet()) {
            weightedSum += (double) entry.getKey() * entry.getValue();
            totalCount += entry.getValue();
        }
        if (totalCount == 0) return Double.NaN;
        return Math.round(weightedSum / totalCount * gainFactor * 100.0) / 100.0;
    }
}
