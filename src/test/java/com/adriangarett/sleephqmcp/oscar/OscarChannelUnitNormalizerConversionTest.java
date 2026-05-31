package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OscarChannelUnitNormalizerConversionTest {

    @Test
    void leakRate_litersPerSecond_convertsToLitersPerMinute() {
        var c = OscarChannelUnitNormalizer.conversionFor("leak_rate", "L/s");
        assertThat(c.unit()).isEqualTo("L/min");
        assertThat(c.factor()).isEqualTo(60.0);
    }

    @Test
    void tidalVolume_liters_convertsToMilliliters() {
        var c = OscarChannelUnitNormalizer.conversionFor("tidal_volume", "L");
        assertThat(c.unit()).isEqualTo("mL");
        assertThat(c.factor()).isEqualTo(1000.0);
    }

    @Test
    void tidalVolume_alreadyMilliliters_identity() {
        var c = OscarChannelUnitNormalizer.conversionFor("tidal_volume", "mL");
        assertThat(c.unit()).isEqualTo("mL");
        assertThat(c.factor()).isEqualTo(1.0);
    }

    @Test
    void otherChannel_identity_preservesUnit() {
        var c = OscarChannelUnitNormalizer.conversionFor("pressure", "cmH2O");
        assertThat(c.unit()).isEqualTo("cmH2O");
        assertThat(c.factor()).isEqualTo(1.0);
    }

    @Test
    void leakRate_blankUnit_withLpsMagnitude_convertsToLitersPerMinute() {
        var c = OscarChannelUnitNormalizer.conversionFor("leak_rate", "", java.util.List.of(0.22, 0.18, 0.5));
        assertThat(c.unit()).isEqualTo("L/min");
        assertThat(c.factor()).isEqualTo(60.0);
    }

    @Test
    void leakRate_blankUnit_withLpmMagnitude_identity() {
        var c = OscarChannelUnitNormalizer.conversionFor("leak_rate", "", java.util.List.of(12.0, 8.0, 15.0));
        assertThat(c.unit()).isEqualTo("L/min");
        assertThat(c.factor()).isEqualTo(1.0);
    }

    @Test
    void tidalVolume_blankUnit_liters_convertsToMilliliters() {
        var c = OscarChannelUnitNormalizer.conversionFor("tidal_volume", "", java.util.List.of(0.4, 0.5, 0.6));
        assertThat(c.unit()).isEqualTo("mL");
        assertThat(c.factor()).isEqualTo(1000.0);
    }

    @Test
    void tidalVolume_blankUnit_alreadyMilliliters_identity() {
        var c = OscarChannelUnitNormalizer.conversionFor("tidal_volume", "", java.util.List.of(400.0, 500.0));
        assertThat(c.unit()).isEqualTo("mL");
        assertThat(c.factor()).isEqualTo(1.0);
    }
}
