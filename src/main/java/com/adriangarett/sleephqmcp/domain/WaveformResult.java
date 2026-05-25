package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WaveformResult(
        @JsonProperty("filename")         String filename,
        @JsonProperty("start_datetime")   String startDatetime,
        @JsonProperty("duration_seconds") double durationSeconds,
        @JsonProperty("channels")         List<WaveformChannel> channels
) {}
