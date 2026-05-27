package com.adriangarett.sleephqmcp.domain;

public record ChannelStatistics(
        String fieldName,
        String unit,
        double avg,
        double min,
        double max,
        double percentile,
        String minAt,
        String maxAt,
        int sampleCount
) {}
