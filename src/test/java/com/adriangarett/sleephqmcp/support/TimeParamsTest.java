package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeParamsTest {

    @Test
    void parseOrNull_returnsNullForBlank() {
        assertThat(TimeParams.parseOrNull(null)).isNull();
        assertThat(TimeParams.parseOrNull("")).isNull();
        assertThat(TimeParams.parseOrNull("  ")).isNull();
    }

    @Test
    void parseOrNull_parsesValidTime() {
        assertThat(TimeParams.parseOrNull("03:14:00")).isEqualTo(LocalTime.of(3, 14, 0));
        assertThat(TimeParams.parseOrNull("23:59:59")).isEqualTo(LocalTime.of(23, 59, 59));
    }

    @Test
    void parseOrNull_throwsOnInvalidFormat() {
        assertThatThrownBy(() -> TimeParams.parseOrNull("3:14"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HH:mm:ss");
    }

    @Test
    void requireTime_rejectsBlank() {
        assertThatThrownBy(() -> TimeParams.requireTime(null, "fromTime"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromTime");
    }

    @Test
    void requireTime_parsesValid() {
        assertThat(TimeParams.requireTime("01:02:03", "fromTime")).isEqualTo(LocalTime.of(1, 2, 3));
    }
}
