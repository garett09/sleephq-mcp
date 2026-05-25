package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Caps MCP payload size by decimating channel sample arrays for LLM clients.
 */
public final class WaveformDownsampler {

    /** ~2 min at 25 Hz — safe for chat/API context limits. */
    public static final int MAX_SAMPLES_PER_CHANNEL = 500;

    private WaveformDownsampler() {
    }

    public static WaveformResult compact(WaveformResult result) {
        List<WaveformChannel> channels = new ArrayList<>();
        boolean anyDownsampled = false;
        for (WaveformChannel channel : result.channels()) {
            int original = channel.samples().size();
            if (original <= MAX_SAMPLES_PER_CHANNEL) {
                channels.add(channel);
                continue;
            }
            anyDownsampled = true;
            channels.add(new WaveformChannel(
                    channel.label(),
                    channel.sampleRate(),
                    channel.unit(),
                    decimate(channel.samples(), MAX_SAMPLES_PER_CHANNEL),
                    original,
                    stepFor(original, MAX_SAMPLES_PER_CHANNEL)
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
        int step = stepFor(n, maxCount);
        List<Double> out = new ArrayList<>(maxCount);
        for (int i = 0; i < n && out.size() < maxCount; i += step) {
            out.add(samples.get(i));
        }
        return out;
    }

    static int stepFor(int originalCount, int maxCount) {
        return Math.max(1, (int) Math.ceil((double) originalCount / maxCount));
    }
}
