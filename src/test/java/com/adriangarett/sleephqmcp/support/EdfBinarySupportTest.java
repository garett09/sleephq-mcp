package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EdfBinarySupportTest {

    @Test
    void scale_normalRange_mapsDigitalToPhysical() {
        // Midpoint of a symmetric digital range maps near the physical midpoint.
        double v = EdfBinarySupport.scale((short) 0, -32768, 32767, -1.0, 1.0);
        assertThat(v).isCloseTo(0.0, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void scale_flatChannel_digMaxEqualsDigMin_returnsPhysMin() {
        assertThat(EdfBinarySupport.scale((short) 123, 5, 5, 2.0, 9.0)).isEqualTo(2.0);
    }

    @Test
    void scale_invertedRange_returnsPhysMin_notSignFlippedGarbage() {
        // digMax < digMin is a corrupt header. Must NOT emit a plausible-but-wrong scaled value.
        assertThat(EdfBinarySupport.scale((short) 100, 32767, -32768, -1.0, 1.0)).isEqualTo(-1.0);
    }

    @Test
    void scale_nonFinitePhysicalBounds_returnsSafeValue() {
        assertThat(EdfBinarySupport.scale((short) 100, -32768, 32767, Double.NaN, 1.0)).isEqualTo(0.0);
    }
}
