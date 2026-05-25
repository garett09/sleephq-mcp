package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OximetrySample(
        @JsonProperty("elapsed_seconds") double elapsedSeconds,
        @JsonProperty("spo2") int spo2,
        @JsonProperty("pulse_bpm") int pulseBpm,
        @JsonProperty("motion") int motion,
        @JsonProperty("invalid") boolean invalid
) {}
