package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Per-channel nightly statistical summary for {@code get-sleephq-night}. The channel name is the map
 * key in the response (e.g. {@code cpap.channels.pressure}), so this record carries only values.
 * {@code markers} is null when the channel has no clinical markers (omitted via {@code NON_NULL}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NightChannelSummary(
        @JsonProperty("unit") String unit,
        @JsonProperty("p99") double p99,
        @JsonProperty("p99_5") double p995,
        @JsonProperty("p95") double p95,
        @JsonProperty("median") double median,
        @JsonProperty("min") double min,
        @JsonProperty("max") double max,
        @JsonProperty("avg") double avg,
        @JsonProperty("count") int count,
        @JsonProperty("markers") Map<String, Double> markers
) {}
