package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JournalFeelingScoreTest {

    @Test
    void labelFor_knownScores_returnsSleepHqMoodLabels() {
        assertThat(JournalFeelingScore.labelFor(1)).contains("Awful");
        assertThat(JournalFeelingScore.labelFor(3)).contains("Okay");
        assertThat(JournalFeelingScore.labelFor(5)).contains("Great");
    }

    @Test
    void displayFor_score3_includesLabelAndNumber() {
        assertThat(JournalFeelingScore.displayFor(3)).isEqualTo("Okay (3)");
    }

    @Test
    void labelFor_outOfRange_returnsEmpty() {
        assertThat(JournalFeelingScore.labelFor(0)).isEmpty();
        assertThat(JournalFeelingScore.labelFor(9)).isEmpty();
    }
}
