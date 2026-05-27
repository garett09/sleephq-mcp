package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OscarChannelUnitNormalizerTest {

    @Test
    void normalizeTidalVolume_fractionalLiters_reportsMilliliters() {
        ChannelStatistics raw = stat("tidal_volume", "L", 0.373, 0.0, 1.0, 0.5);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("mL");
        assertThat(normalized.avg()).isEqualTo(373.0);
        assertThat(normalized.max()).isEqualTo(1000.0);
    }

    @Test
    void normalizeTidalVolume_largeValues_staysMilliliters() {
        ChannelStatistics raw = stat("tidal_volume", "mL", 450.0, 200.0, 680.0, 600.0);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("mL");
        assertThat(normalized.avg()).isEqualTo(450.0);
    }

    @Test
    void normalizeTidalVolume_multiLiterRange_reportsLiters() {
        ChannelStatistics raw = stat("tidal_volume", "L", 2.5, 1.8, 3.2, 3.0);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("L");
        assertThat(normalized.avg()).isEqualTo(2.5);
    }

    @Test
    void normalizeLeak_litersPerSecond_convertsToLitersPerMinute() {
        ChannelStatistics raw = stat("leak", "L/s", 0.037, 0.0, 1.12, 0.16);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("L/min");
        assertThat(normalized.avg()).isEqualTo(2.22);
        assertThat(normalized.max()).isEqualTo(67.2);
    }

    @Test
    void normalizeFlowBrp_litersPerSecond_convertsToLitersPerMinute() {
        ChannelStatistics raw = stat("flow_brp", "L/s", 0.002, -1.89, 2.214, 0.384);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("L/min");
        assertThat(normalized.avg()).isEqualTo(0.12);
        assertThat(normalized.max()).isEqualTo(132.84);
        assertThat(normalized.min()).isEqualTo(-113.4);
    }

    private static ChannelStatistics stat(
            String field, String unit, double avg, double min, double max, double p95) {
        return new ChannelStatistics(field, unit, avg, min, max, p95, "00:00:00", "01:00:00", 100);
    }
}
