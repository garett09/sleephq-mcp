package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JournalSleepStagesSummaryTest {

    @Test
    void summarize_appleHealthSegments_computesMinutesByStage() throws Exception {
        String raw = new String(
                getClass().getResourceAsStream("/journal/sleep-stages-apple-health-segments.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var parsed = JournalSleepStagesParser.tryParse(raw);
        var summary = JournalSleepStagesSummary.summarize(parsed);

        assertThat(summary).isNotNull();
        assertThat(summary.path("segment_count").asInt()).isEqualTo(4);
        assertThat(summary.path("stage_type_legend").path("3").asText()).isEqualTo("core");
        assertThat(summary.path("minutes_by_stage").path("core").asDouble()).isEqualTo(21.6);
        assertThat(summary.path("minutes_by_stage").path("awake").asDouble()).isEqualTo(20.1);
        assertThat(summary.path("minutes_by_stage").path("deep").asDouble()).isEqualTo(81.4);
        assertThat(summary.path("minutes_by_stage").path("rem").asDouble()).isEqualTo(30.0);
        assertThat(summary.path("asleep_minutes").asDouble()).isGreaterThan(100.0);
        assertThat(summary.path("sleep_window").path("start").asText()).isEqualTo("2026-05-24T17:26:58Z");
    }

    @Test
    void summarize_overlappingSegments_mergesTimelineAndFlagsOverlap() throws Exception {
        String raw = new String(
                getClass().getResourceAsStream("/journal/sleep-stages-overlapping-segments.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var parsed = JournalSleepStagesParser.tryParse(raw);
        var summary = JournalSleepStagesSummary.summarize(parsed);

        assertThat(summary).isNotNull();
        assertThat(summary.path("aggregation_method").asText()).isEqualTo("merged_timeline");
        assertThat(summary.path("segments_overlap_detected").asBoolean()).isTrue();
        assertThat(summary.path("minutes_by_stage").path("deep").asDouble()).isEqualTo(60.0);
        assertThat(summary.path("minutes_by_stage").path("core").asDouble()).isEqualTo(30.0);
        assertThat(summary.path("asleep_minutes").asDouble()).isEqualTo(90.0);
        assertThat(summary.path("sleep_window").path("span_minutes").asDouble()).isEqualTo(90.0);
    }

    @Test
    void summarize_null_returnsNull() {
        assertThat(JournalSleepStagesSummary.summarize(null)).isNull();
    }
}
