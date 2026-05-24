package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResponse;
import com.adriangarett.sleephqmcp.domain.WaveformSample;
import com.adriangarett.sleephqmcp.domain.WaveformStats;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the two-mode waveform contract: no window → stats only;
 * with window → raw native-rate samples for that window only. Never silently decimates.
 */
@Service
public class WaveformService {

    private final SleepHqClient client;

    public WaveformService(SleepHqClient client) {
        this.client = client;
    }

    public WaveformResponse fetch(WaveformChannel channel, String machineDateId,
                                  @Nullable LocalTime from, @Nullable LocalTime to) {
        String rawJson = client.getNightWaveform(machineDateId, channel.pathSegment());
        JsonNode node;
        try {
            node = JsonApi.parse(rawJson);
        } catch (Exception e) {
            return new WaveformResponse.Passthrough(channel, rawJson,
                    "Response was not valid JSON; returned raw body for inspection.");
        }

        List<WaveformSample> samples = extractSamples(node, channel.nativeSampleRateHz());
        if (samples == null) {
            return new WaveformResponse.Passthrough(channel, rawJson,
                    "Could not parse waveform shape automatically; returned raw payload. "
                            + "Inspect and adjust WaveformService.extractSamples to match.");
        }

        if (from == null && to == null) {
            double[] values = samples.stream().mapToDouble(WaveformSample::v).toArray();
            return new WaveformResponse.Stats(channel,
                    WaveformStats.from(values, channel.nativeSampleRateHz()));
        }

        List<WaveformSample> window = sliceByWindow(samples, from, to);
        return new WaveformResponse.Raw(channel, from, to, window);
    }

    /**
     * Heuristically extract a numeric sample list from common SleepHQ payload shapes.
     * Returns {@code null} if no recognized shape matched — caller should pass through.
     */
    static List<WaveformSample> extractSamples(JsonNode root, double sampleRateHz) {
        // 1) Flat numeric array under `data`
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0 && data.get(0).isNumber()) {
            return numericArrayToSamples(data, sampleRateHz);
        }

        // 2) Array of {t, v} objects under `data`
        if (data.isArray() && data.size() > 0 && data.get(0).isObject()
                && data.get(0).has("t") && data.get(0).has("v")) {
            List<WaveformSample> out = new ArrayList<>(data.size());
            for (JsonNode element : data) {
                out.add(new WaveformSample(element.path("t").asDouble(), element.path("v").asDouble()));
            }
            return out;
        }

        // 3) JSON:API envelope: data.attributes.{values | data}
        JsonNode attributes = data.path("attributes");
        if (attributes.isObject()) {
            JsonNode values = firstArray(attributes, "values", "data", "samples", "points");
            if (values != null && values.size() > 0 && values.get(0).isNumber()) {
                double intervalSeconds = attributes.path("interval_seconds").asDouble(1.0 / sampleRateHz);
                double rate = intervalSeconds > 0 ? 1.0 / intervalSeconds : sampleRateHz;
                return numericArrayToSamples(values, rate);
            }
        }

        return null;
    }

    private static List<WaveformSample> numericArrayToSamples(JsonNode array, double sampleRateHz) {
        double interval = sampleRateHz > 0 ? 1.0 / sampleRateHz : 1.0;
        List<WaveformSample> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            out.add(new WaveformSample(i * interval, array.get(i).asDouble()));
        }
        return out;
    }

    private static JsonNode firstArray(JsonNode parent, String... keys) {
        for (String key : keys) {
            JsonNode child = parent.path(key);
            if (child.isArray()) return child;
        }
        return null;
    }

    static List<WaveformSample> sliceByWindow(List<WaveformSample> samples,
                                              @Nullable LocalTime from, @Nullable LocalTime to) {
        if (samples.isEmpty()) return samples;
        // Samples carry seconds-from-start. Convert HH:MM:SS to seconds-from-midnight and
        // assume the recording starts at midnight unless the data tells us otherwise.
        // (SleepHQ wraps midnight-spanning sessions; if the agent supplies a window the
        //  data is already keyed to that night's clock.)
        double fromSec = from == null ? samples.get(0).t() : from.toSecondOfDay();
        double toSec = to == null ? samples.get(samples.size() - 1).t() : to.toSecondOfDay();
        if (toSec < fromSec) {
            // window crosses midnight — wrap by adding 24h to the end
            toSec += 24 * 3600;
        }
        List<WaveformSample> out = new ArrayList<>();
        for (WaveformSample sample : samples) {
            if (sample.t() >= fromSec && sample.t() <= toSec) {
                out.add(sample);
            }
        }
        return out;
    }
}
