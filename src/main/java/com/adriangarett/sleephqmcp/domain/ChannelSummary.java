package com.adriangarett.sleephqmcp.domain;

public record ChannelSummary(
        Double avg,
        Double min,
        Double max,
        Double wavg,
        Double count,
        Double sum
) {}
