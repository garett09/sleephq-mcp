package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WaveformWindowPlan(
        @JsonProperty("anchor_requested") String anchorRequested,
        @JsonProperty("anchor_resolved") String anchorResolved,
        @JsonProperty("start_minute") int startMinute,
        @JsonProperty("start_seconds") int startSeconds,
        @JsonProperty("lead_in_minutes") int leadInMinutes,
        @JsonProperty("max_minutes") int maxMinutes,
        @JsonProperty("reason") String reason,
        @JsonProperty("evidence") List<WindowEvidence> evidence,
        @JsonProperty("alignment_confidence") String alignmentConfidence) {

    public WaveformWindowPlan {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
