package com.adriangarett.sleephqmcp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaveformChannelTest {

    @Test
    void requireKnownPathSegment_acceptsFlowRateSegment() {
        assertThat(WaveformChannel.requireKnownPathSegment("flow_rate_data")).isEqualTo("flow_rate_data");
    }

    @Test
    void requireKnownPathSegment_acceptsTidalVolume() {
        assertThat(WaveformChannel.requireKnownPathSegment("tidal_volume_data")).isEqualTo("tidal_volume_data");
    }

    @Test
    void parseChannelList_acceptsEnumNamesAndSegments() {
        assertThat(WaveformChannel.parseChannelList("flow_rate , pressure_data"))
                .containsExactly(WaveformChannel.FLOW_RATE, WaveformChannel.PRESSURE);
    }

    @Test
    void parseChannelList_rejectsUnknownToken() {
        assertThatThrownBy(() -> WaveformChannel.parseChannelList("flow_rate,nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void parseChannelList_rejectsMoreThanSixChannels() {
        assertThatThrownBy(() -> WaveformChannel.parseChannelList(
                "flow_rate,pressure,leak,spo2,pulse_rate,tidal_volume,flow_rate"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6");
    }

    @Test
    void requireKnownPathSegment_rejectsUnknown() {
        assertThatThrownBy(() -> WaveformChannel.requireKnownPathSegment("evil_segment"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
