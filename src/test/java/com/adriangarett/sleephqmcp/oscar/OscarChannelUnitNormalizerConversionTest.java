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
}
