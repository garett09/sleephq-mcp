package com.adriangarett.sleephqmcp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaveformChannelTest {

    @Test
    void requireKnownPathSegment_acceptsEnumSegments() {
        assertThat(WaveformChannel.requireKnownPathSegment("flow_rate_data")).isEqualTo("flow_rate_data");
    }

    @Test
    void requireKnownPathSegment_rejectsUnknown() {
        assertThatThrownBy(() -> WaveformChannel.requireKnownPathSegment("evil_segment"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
