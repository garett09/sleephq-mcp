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
        for (Map.Entry<Integer, ChannelSummary> entry : session.channels().entrySet()) {
            int id = entry.getKey();
            if (OscarChannelIdClassification.isEventChannel(id)) {
                continue;
            }
            ChannelSummary summary = entry.getValue();
            if (summary.avg() == null && summary.min() == null && summary.max() == null) {
                continue;
            }
            String field = OscarChannelCatalog.fieldName(id);
            String unit = OscarChannelCatalog.find(id).map(OscarChannelCatalog.ChannelMeta::unit).orElse("");
            ChannelStatistics raw = new ChannelStatistics(
                    field,
                    unit,
                    round(summary.avg()),
                    round(summary.min()),
                    round(summary.max()),
                    round(summary.max()),
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
