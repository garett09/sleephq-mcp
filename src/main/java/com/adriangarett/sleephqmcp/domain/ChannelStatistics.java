package com.adriangarett.sleephqmcp.domain;

public record ChannelStatistics(
        String fieldName,
        String unit,
        double avg,
        double min,
        double max,
        double percentile,
        double median,
        String minAt,
        String maxAt,
        int minAtSeconds,
        int maxAtSeconds,
        int sampleCount
) {
    /** Session-relative offset is unknown (e.g. summary-only data with no sample timing). */
    public static final int OFFSET_UNKNOWN = -1;
}
