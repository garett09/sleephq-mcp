package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaveformDownsamplerTest {

    @Test
    void compact_whenUnderCap_returnsUnchanged() {
        List<Double> samples = List.of(1.0, 2.0, 3.0);
        WaveformResult in = new WaveformResult(
                "f.edf", "2026-05-20T21:00:00", 60.0,
                List.of(new WaveformChannel("Flow", 25.0, "L/s", samples))
        );

        WaveformResult out = WaveformDownsampler.compact(in);

        assertThat(out.samplesDownsampled()).isNull();
        assertThat(out.channels().getFirst().samples()).hasSize(3);
        assertThat(out.channels().getFirst().sampleCountOriginal()).isNull();
    }

    @Test
    void compact_whenOverCap_decimatesAndFlags() {
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < 15_000; i++) {
            samples.add((double) i);
        }
        WaveformResult in = new WaveformResult(
                "f.edf", "2026-05-20T21:00:00", 600.0,
                List.of(new WaveformChannel("Flow.40ms", 25.0, "L/s", samples))
        );

        WaveformResult out = WaveformDownsampler.compact(in);

        assertThat(out.samplesDownsampled()).isTrue();
        WaveformChannel ch = out.channels().getFirst();
        assertThat(ch.samples()).hasSizeLessThanOrEqualTo(WaveformDownsampler.MAX_SAMPLES_PER_CHANNEL);
        assertThat(ch.sampleCountOriginal()).isEqualTo(15_000);
        assertThat(ch.downsampleStep()).isGreaterThan(1);
    }

    @Test
    void decimate_nearCap_returnsExactlyCapSamples() {
        java.util.List<Double> samples = new java.util.ArrayList<>();
        for (int i = 0; i < 501; i++) {
            samples.add((double) i);
        }
        java.util.List<Double> out = WaveformDownsampler.decimate(samples, 500);
        assertThat(out).hasSize(500);
        assertThat(out.get(0)).isEqualTo(0.0);
        assertThat(out.get(out.size() - 1)).isEqualTo(500.0);
    }
}
