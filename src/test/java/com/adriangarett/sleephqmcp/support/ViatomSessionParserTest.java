package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.OximetryResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViatomSessionParserTest {

    @Test
    void parse_vld3Fixture_returnsSpo2AndPulseSeries() {
        byte[] data = ViatomTestFixtures.vld3Session();
        OximetryResult result = ViatomSessionParser.parse(data, "20260525011013-1721", Integer.MAX_VALUE / 2);

        assertThat(result.source()).isEqualTo("viatom_vld3");
        assertThat(result.startDatetime()).isEqualTo("2026-05-25T01:10:13");
        assertThat(result.samples()).hasSize(4);
        assertThat(result.intervalSeconds()).isEqualTo(4.0);
        assertThat(result.samples().get(0).spo2()).isEqualTo(98);
        assertThat(result.samples().get(0).pulseBpm()).isEqualTo(72);
        assertThat(result.samples().get(0).invalid()).isFalse();
    }

    @Test
    void parse_maxSeconds_capsSamples() {
        byte[] data = ViatomTestFixtures.vld3Session();
        OximetryResult result = ViatomSessionParser.parse(data, "20260525011013-1721", 8);
        assertThat(result.samples()).hasSize(2);
    }

    @Test
    void parse_o2RingSSignature_matchesSleepHqHeaderHex() {
        byte[] data = ViatomTestFixtures.o2RingSSession();
        assertThat(ViatomSessionParser.hexPrefix(data, 4)).isEqualTo("01030000");

        OximetryResult result = ViatomSessionParser.parse(data, "20260519011013-1721", Integer.MAX_VALUE / 2);

        assertThat(result.source()).isEqualTo("viatom_o2ring_s");
        assertThat(result.startDatetime()).isEqualTo("2026-05-19T01:10:13");
        assertThat(result.intervalSeconds()).isEqualTo(1.0);
        assertThat(result.durationSeconds()).isEqualTo(4.0);
        assertThat(result.samples()).hasSize(4);
        assertThat(result.samples().get(0).spo2()).isEqualTo(98);
        assertThat(result.samples().get(0).pulseBpm()).isEqualTo(72);
    }

    @Test
    void parse_invalidMagic_throwsWithHexPrefix() {
        byte[] bad = {0x00, 0x00, 0x01, 0x02};
        assertThatThrownBy(() -> ViatomSessionParser.parse(bad, "x", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header hex:");
    }
}
