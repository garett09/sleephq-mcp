package com.adriangarett.sleephqmcp.domain;

import java.util.Arrays;

public record WaveformStats(
        int sampleCount,
        double durationSeconds,
        double min,
        double max,
        double avg,
        double p95
) {

    public static WaveformStats from(double[] values, double nativeSampleRateHz) {
        if (values == null || values.length == 0) {
            return new WaveformStats(0, 0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double min = sorted[0];
        double max = sorted[sorted.length - 1];
        double sum = 0;
        for (double v : values) sum += v;
        double avg = sum / values.length;
        int p95Index = (int) Math.ceil(0.95 * sorted.length) - 1;
        p95Index = Math.max(0, Math.min(sorted.length - 1, p95Index));
        double p95 = sorted[p95Index];
        double duration = nativeSampleRateHz > 0 ? values.length / nativeSampleRateHz : 0.0;
        return new WaveformStats(values.length, duration, min, max, avg, p95);
    }
}
