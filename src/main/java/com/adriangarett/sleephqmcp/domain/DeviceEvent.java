package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeviceEvent(
        @JsonProperty("offset") String offset,
        @JsonProperty("start_seconds") double startSeconds,
        @JsonProperty("duration_seconds") double durationSeconds,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("label") String label,
        @JsonProperty("code") String code
) {}
