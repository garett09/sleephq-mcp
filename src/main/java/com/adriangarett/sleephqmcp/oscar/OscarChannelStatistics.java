package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OscarChannelStatistics {

    private OscarChannelStatistics() {}

    public static Map<String, ChannelStatistics> fromSummarySession(OscarSession session) {
        Map<String, ChannelStatistics> stats = new LinkedHashMap<>();
        for (Map.Entry<String, ChannelSummary> entry : session.channels().entrySet()) {
            String code = entry.getKey();
            if (OscarChannelMapper.isEventChannel(code)) {
                continue;
            }
            ChannelSummary summary = entry.getValue();
            if (summary.avg() == null && summary.min() == null && summary.max() == null) {
                continue;
            }
            String field = OscarChannelMapper.fieldName(code);
            String unit = OscarChannelMapper.unit(code);
            ChannelStatistics raw = new ChannelStatistics(
                    field,
                    unit,
                    round(summary.avg()),
                    round(summary.min()),
                    round(summary.max()),
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    null,
                    null,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    0);
            stats.put(field, OscarChannelUnitNormalizer.normalize(raw));
        }
        return stats;
    }

    public static Map<String, ChannelStatistics> fromSummarySession(
            OscarSession session, Map<String, OscarChannelHistogram> histograms) {
        Map<String, OscarChannelHistogram> safeHistograms = histograms == null ? Map.of() : histograms;
        Map<String, ChannelStatistics> stats = new LinkedHashMap<>();
        for (Map.Entry<String, ChannelSummary> entry : session.channels().entrySet()) {
            String code = entry.getKey();
            if (OscarChannelMapper.isEventChannel(code)) {
                continue;
            }
            ChannelSummary summary = entry.getValue();
            if (summary.avg() == null && summary.min() == null && summary.max() == null) {
                continue;
            }
            String field = OscarChannelMapper.fieldName(code);
            String unit = OscarChannelMapper.unit(code);

            double p95 = Double.NaN;
            double p995 = Double.NaN;
            double median = Double.NaN;

            OscarChannelHistogram hist = safeHistograms.get(code);
            if (hist != null && !hist.buckets().isEmpty()) {
                p95    = OscarHistogram.percentile(hist.buckets(), hist.gainFactor(), 95);
                p995   = OscarHistogram.percentile(hist.buckets(), hist.gainFactor(), 99.5);
                median = OscarHistogram.percentile(hist.buckets(), hist.gainFactor(), 50);
            }

            ChannelStatistics raw = new ChannelStatistics(
                    field, unit,
                    round(summary.avg()),
                    round(summary.min()),
                    round(summary.max()),
                    p95, p995, median,
                    null, null,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    0);
            stats.put(field, OscarChannelUnitNormalizer.normalize(raw));
        }
        return stats;
    }

    public static void mergePreferEdf(Map<String, ChannelStatistics> target, Map<String, ChannelStatistics> edf) {
        edf.forEach(target::put);
    }

    private static double round(Double value) {
        if (value == null || value.isNaN()) {
            return 0;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
