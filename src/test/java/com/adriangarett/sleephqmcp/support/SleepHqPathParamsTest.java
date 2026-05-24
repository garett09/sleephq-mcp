package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SleepHqPathParamsTest {

    @Test
    void requireResourceId_acceptsAlphanumericUnderscoreHyphen() {
        assertThat(SleepHqPathParams.requireResourceId("team_12-AB", "teamId")).isEqualTo("team_12-AB");
    }

    @Test
    void requireResourceId_rejectsSlashes() {
        assertThatThrownBy(() -> SleepHqPathParams.requireResourceId("a/b", "teamId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId");
    }

    @Test
    void requireResourceId_rejectsDotDot() {
        assertThatThrownBy(() -> SleepHqPathParams.requireResourceId("..", "machineId"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireCalendarDate_acceptsIsoDate() {
        assertThat(SleepHqPathParams.requireCalendarDate("2024-01-15", "date")).isEqualTo("2024-01-15");
    }

    @Test
    void requireCalendarDate_rejectsInvalid() {
        assertThatThrownBy(() -> SleepHqPathParams.requireCalendarDate("01-15-2024", "date"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionalResourceId_returnsNullForBlank() {
        assertThat(SleepHqPathParams.optionalResourceId("  ", "machineId")).isNull();
    }
}
