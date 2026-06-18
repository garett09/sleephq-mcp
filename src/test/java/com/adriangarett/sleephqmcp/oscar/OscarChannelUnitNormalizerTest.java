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
    void normalizeLeak_blankUnit_lpsMagnitude_convertsToLitersPerMinute() {
        // Blank EDF unit + L/s-magnitude samples (max < 5) must infer ×60, matching get-sleephq-night.
        ChannelStatistics raw = stat("leak", "", 0.037, 0.0, 1.12, 0.16);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("L/min");
        assertThat(normalized.max()).isEqualTo(67.2); // 1.12 × 60
    }

    @Test
    void normalizeLeak_blankUnit_lpmMagnitude_staysIdentity() {
        // Blank unit but already-L/min magnitude (max >= 5) must NOT be scaled.
        ChannelStatistics raw = stat("leak", "", 12.0, 0.0, 38.0, 24.0);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("L/min");
        assertThat(normalized.max()).isEqualTo(38.0);
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

    @Test
    void normalizeTidalVolume_nanMedianSurvivesScaling() {
        ChannelStatistics raw = stat("tidal_volume", "L", 0.373, 0.0, 1.0, 0.5); // median defaults to NaN
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(Double.isNaN(normalized.median())).isTrue();
    }

    @Test
    void normalizeTidalVolume_nanPercentileSurvivesScaling() {
        ChannelStatistics raw = new ChannelStatistics(
                "tidal_volume", "L", 0.373, 0.0, 1.0, Double.NaN, Double.NaN, Double.NaN,
                null, null, ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(Double.isNaN(normalized.percentile())).isTrue();
        assertThat(normalized.avg()).isEqualTo(373.0);
    }

    @Test
    void normalizeLeak_nanPercentileSurvivesScaling() {
        ChannelStatistics raw = new ChannelStatistics(
                "leak", "L/s", 0.037, 0.0, 1.12, Double.NaN, Double.NaN, Double.NaN,
                null, null, ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(Double.isNaN(normalized.percentile())).isTrue();
        assertThat(normalized.avg()).isEqualTo(2.22);
    }

    @Test
    void normalizeTidalVolume_scalesMedianWithOtherStats() {
        ChannelStatistics raw = stat("tidal_volume", "L", 0.373, 0.0, 1.0, 0.5, 0.36);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("mL");
        assertThat(normalized.median()).isEqualTo(360.0);
    }

    private static ChannelStatistics stat(
            String field, String unit, double avg, double min, double max, double p95) {
        return stat(field, unit, avg, min, max, p95, Double.NaN);
    }

    private static ChannelStatistics stat(
            String field, String unit, double avg, double min, double max, double p95, double median) {
        return new ChannelStatistics(field, unit, avg, min, max, p95, Double.NaN, median,
                "00:00:00", "01:00:00", 0, 3600, 100);
    }
}
