package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaveformResult(
        @JsonProperty("filename")              String filename,
        @JsonProperty("start_datetime")        String startDatetime,
        @JsonProperty("duration_seconds")      double durationSeconds,
        @JsonProperty("channels")              List<WaveformChannel> channels,
        @JsonProperty("samples_downsampled")   Boolean samplesDownsampled
) {
    public WaveformResult(String filename, String startDatetime, double durationSeconds, List<WaveformChannel> channels) {
        this(filename, startDatetime, durationSeconds, channels, null);
    }
}
