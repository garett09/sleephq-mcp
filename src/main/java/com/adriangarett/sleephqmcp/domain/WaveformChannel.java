package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaveformChannel(
        @JsonProperty("label")                  String label,
        @JsonProperty("sample_rate")           double sampleRate,
        @JsonProperty("unit")                  String unit,
        @JsonProperty("samples")               List<Double> samples,
        @JsonProperty("sample_count_original") Integer sampleCountOriginal,
        @JsonProperty("downsample_step")       Integer downsampleStep
) {
    public WaveformChannel(String label, double sampleRate, String unit, List<Double> samples) {
        this(label, sampleRate, unit, samples, null, null);
    }
}
