package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OximetryResult(
        @JsonProperty("filename") String filename,
        @JsonProperty("start_datetime") String startDatetime,
        @JsonProperty("duration_seconds") double durationSeconds,
        @JsonProperty("interval_seconds") double intervalSeconds,
        @JsonProperty("source") String source,
        @JsonProperty("samples") List<OximetrySample> samples
) {}
