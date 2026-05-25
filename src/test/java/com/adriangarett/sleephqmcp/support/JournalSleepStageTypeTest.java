package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JournalSleepStageTypeTest {

    @Test
    void displayLabelFor_core_returnsLight() {
        assertThat(JournalSleepStageType.displayLabelFor("core")).isEqualTo("light");
    }
}
