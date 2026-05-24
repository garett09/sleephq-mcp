package com.adriangarett.sleephqmcp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaveformStatsTest {

    @Test
    void from_computesBasicStats() {
        double[] values = {1.0, 2.0, 3.0, 4.0, 5.0};
        WaveformStats stats = WaveformStats.from(values, 25.0);

        assertThat(stats.sampleCount()).isEqualTo(5);
        assertThat(stats.min()).isEqualTo(1.0);
        assertThat(stats.max()).isEqualTo(5.0);
        assertThat(stats.avg()).isEqualTo(3.0);
        assertThat(stats.durationSeconds()).isEqualTo(5 / 25.0);
    }

    @Test
    void from_p95IsHighEnd() {
        double[] values = new double[100];
        for (int i = 0; i < 100; i++) values[i] = i;
        WaveformStats stats = WaveformStats.from(values, 1.0);
        assertThat(stats.p95()).isBetween(94.0, 95.0);
    }

    @Test
    void from_emptyReturnsNaN() {
        WaveformStats stats = WaveformStats.from(new double[0], 25.0);
        assertThat(stats.sampleCount()).isZero();
        assertThat(stats.min()).isNaN();
    }
}
