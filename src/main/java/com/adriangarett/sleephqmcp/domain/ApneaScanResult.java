package com.adriangarett.sleephqmcp.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ApneaScanResult(
        @JsonProperty("filename")           String filename,
        @JsonProperty("start_datetime")     String startDatetime,
        @JsonProperty("duration_seconds")   double durationSeconds,
        @JsonProperty("channel_scanned")    String channelScanned,
        @JsonProperty("threshold")          double threshold,
        @JsonProperty("events")             List<ApneaEvent> events
) {}
