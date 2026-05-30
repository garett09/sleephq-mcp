package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NightDateGroupingTest {

    @Test
    void cleanDate_stripsDashes_andValidates() {
        assertThat(NightDateGrouping.cleanDate("2026-04-17")).isEqualTo("20260417");
        assertThatThrownBy(() -> NightDateGrouping.cleanDate("2026-4-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void parseStamp_handlesResMedAndO2Formats() {
        assertThat(NightDateGrouping.parseStamp("20260418_015119_PLD.edf"))
                .isEqualTo(LocalDateTime.of(2026, 4, 18, 1, 51, 19));
        assertThat(NightDateGrouping.parseStamp("20260513234701-1721"))
                .isEqualTo(LocalDateTime.of(2026, 5, 13, 23, 47, 1));
        assertThat(NightDateGrouping.parseStamp("no-stamp-here")).isNull();
    }

    @Test
    void inNoonWindow_appliesNoonSplit() {
        // Night 2026-05-17 window [05-17 12:00, 05-18 12:00)
        assertThat(NightDateGrouping.inNoonWindow(LocalDateTime.of(2026, 5, 17, 22, 0), "2026-05-17")).isTrue();
        assertThat(NightDateGrouping.inNoonWindow(LocalDateTime.of(2026, 5, 18, 1, 0), "2026-05-17")).isTrue();
        assertThat(NightDateGrouping.inNoonWindow(LocalDateTime.of(2026, 5, 18, 13, 0), "2026-05-17")).isFalse();
        assertThat(NightDateGrouping.inNoonWindow(LocalDateTime.of(2026, 5, 17, 8, 0), "2026-05-17")).isFalse();
    }

    @Test
    void datalogPathMatches_matchesFolderForDate() {
        assertThat(NightDateGrouping.datalogPathMatches("./DATALOG/20260417/", "2026-04-17")).isTrue();
        assertThat(NightDateGrouping.datalogPathMatches("./DATALOG/20260418/", "2026-04-17")).isFalse();
        assertThat(NightDateGrouping.datalogPathMatches("./SETTINGS/", "2026-04-17")).isFalse();
    }
}
