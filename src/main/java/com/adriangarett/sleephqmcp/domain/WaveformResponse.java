package com.adriangarett.sleephqmcp.domain;

import com.adriangarett.sleephqmcp.support.JsonApi;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sealed response type for waveform endpoints. Tagged with {@code mode} so the LLM never
 * mistakes summary stats for raw samples or vice versa.
 */
public sealed interface WaveformResponse permits
        WaveformResponse.Stats,
        WaveformResponse.Raw,
        WaveformResponse.Passthrough {

    String toJson();

    record Stats(WaveformChannel channel, WaveformStats stats) implements WaveformResponse {
        @Override
        public String toJson() {
            Map<String, Object> body = baseBody(channel, "stats");
            body.put("stats", Map.of(
                    "sample_count", stats.sampleCount(),
                    "duration_seconds", stats.durationSeconds(),
                    "min", stats.min(),
                    "max", stats.max(),
                    "avg", stats.avg(),
                    "p95", stats.p95()
            ));
            body.put("note", "Summary only — call again with fromTime/toTime to retrieve raw samples for a specific window.");
            return JsonApi.toJsonString(body);
        }
    }

    record Raw(WaveformChannel channel, LocalTime from, LocalTime to,
               List<WaveformSample> data) implements WaveformResponse {
        @Override
        public String toJson() {
            Map<String, Object> body = baseBody(channel, "raw");
            body.put("from", from == null ? null : from.toString());
            body.put("to", to == null ? null : to.toString());
            body.put("sample_count", data.size());
            body.put("data", data);
            return JsonApi.toJsonString(body);
        }
    }

    /** Used when we can't parse the SleepHQ response shape — return the raw JSON inline so the LLM still gets the data. */
    record Passthrough(WaveformChannel channel, String rawJson, String note) implements WaveformResponse {
        @Override
        public String toJson() {
            Map<String, Object> body = baseBody(channel, "passthrough");
            body.put("note", note);
            body.put("raw", rawJson);
            return JsonApi.toJsonString(body);
        }
    }

    private static Map<String, Object> baseBody(WaveformChannel channel, String mode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel.name().toLowerCase());
        body.put("label", channel.label());
        body.put("unit", channel.unit());
        body.put("native_sample_rate_hz", channel.nativeSampleRateHz());
        body.put("mode", mode);
        return body;
    }
}
