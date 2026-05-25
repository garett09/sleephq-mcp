package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WaveformChannel(
        @JsonProperty("label")       String label,
        @JsonProperty("sample_rate") double sampleRate,
        @JsonProperty("unit")        String unit,
        @JsonProperty("samples")     List<Double> samples
) {}
