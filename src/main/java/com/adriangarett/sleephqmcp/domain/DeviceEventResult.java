package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DeviceEventResult(
        @JsonProperty("filename") String filename,
        @JsonProperty("start_datetime") String startDatetime,
        @JsonProperty("duration_seconds") double durationSeconds,
        @JsonProperty("source") String source,
        @JsonProperty("events") List<DeviceEvent> events
) {}
