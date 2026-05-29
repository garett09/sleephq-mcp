package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Caps MCP payload size by decimating channel sample arrays for LLM clients.
 */
public final class WaveformDownsampler {

    /** Legacy default when no {@link com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties} is wired. */
    public static final int MAX_SAMPLES_PER_CHANNEL = 500;

    private WaveformDownsampler() {
    }

    public static WaveformResult compact(WaveformResult result) {
        return compact(result, MAX_SAMPLES_PER_CHANNEL);
    }

    public static WaveformResult compact(WaveformResult result, int maxSamplesPerChannel) {
        int cap = Math.max(100, maxSamplesPerChannel);
        List<WaveformChannel> channels = new ArrayList<>();
        boolean anyDownsampled = false;
        for (WaveformChannel channel : result.channels()) {
            int original = channel.samples().size();
            if (original <= cap) {
                channels.add(channel);
                continue;
            }
            anyDownsampled = true;
            channels.add(new WaveformChannel(
                    channel.label(),
                    channel.sampleRate(),
                    channel.unit(),
                    decimate(channel.samples(), cap),
                    original,
                    stepFor(original, cap)
            ));
        }
        if (!anyDownsampled) {
            return result;
        }
        return new WaveformResult(
                result.filename(),
                result.startDatetime(),
                result.durationSeconds(),
                channels,
                true
        );
    }

    static List<Double> decimate(List<Double> samples, int maxCount) {
        int n = samples.size();
        if (n <= maxCount) {
            return samples;
        }
        List<Double> out = new ArrayList<>(maxCount);
        for (int i = 0; i < maxCount; i++) {
            int idx = (int) Math.round((double) i * (n - 1) / (maxCount - 1));
            out.add(samples.get(idx));
        }
        return out;
    }

    static int stepFor(int originalCount, int maxCount) {
        return Math.max(1, (int) Math.ceil((double) originalCount / maxCount));
    }
}
