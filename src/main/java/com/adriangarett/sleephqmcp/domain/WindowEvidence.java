package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WindowEvidence(
        @JsonProperty("source") String source,
        @JsonProperty("label") String label,
        @JsonProperty("start_seconds") Double startSeconds,
        @JsonProperty("timestamp") String timestamp) {
}
