package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    /**
     * Real SleepHQ journal export (2026-05-27, 68 overlapping segments). Stage integers use the
     * SleepHQ dashboard encoding (2=deep, 4=rem, 5=awake); detection must pick that legend and
     * report naive full-span totals matching the dashboard cards (awake 73, core 468, deep 209,
     * rem 191). Guards against the stale-JVM / apple_health mis-detection that collapsed REM.
     */
    @Test
    void summarize_20260527_real_matchesDashboardCards() throws Exception {
        String raw = new String(
                getClass().getResourceAsStream("/journal/2026-05-27-sleep-stages.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var summary = JournalSleepStagesSummary.summarize(JournalSleepStagesParser.tryParse(raw));

        assertThat(summary).isNotNull();
        assertThat(summary.path("legend_profile").asText()).isEqualTo("sleephq_dashboard");
        assertThat(summary.path("reporting_source").asText()).isEqualTo("sleephq_dashboard_naive");

        var reporting = summary.path("minutes_by_stage_for_reporting");
        assertThat(reporting.path("awake").asDouble()).isCloseTo(72.6, within(1.0));
        assertThat(reporting.path("core").asDouble()).isCloseTo(467.7, within(1.0));
        assertThat(reporting.path("deep").asDouble()).isCloseTo(208.8, within(1.0));
        assertThat(reporting.path("rem").asDouble()).isCloseTo(190.9, within(1.0));
    }

    @Test
    void summarize_exposesNaiveAndOverlapAlias() throws Exception {
        String raw = new String(
                getClass().getResourceAsStream("/journal/sleep-stages-overlapping-segments.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var summary = JournalSleepStagesSummary.summarize(JournalSleepStagesParser.tryParse(raw));

        assertThat(summary.path("minutes_by_stage_naive").path("deep").isNumber()).isTrue();
        assertThat(summary.path("overlap_detected").asBoolean())
                .isEqualTo(summary.path("segments_overlap_detected").asBoolean());
    }

    @Test
    void summarize_sleephqDashboardEncoding_matchesUiStyleTotals() throws Exception {
        String raw = new String(
                getClass().getResourceAsStream("/journal/sleep-stages-sleephq-dashboard-encoding.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var summary = JournalSleepStagesSummary.summarize(JournalSleepStagesParser.tryParse(raw));

        assertThat(summary.path("legend_profile").asText()).isEqualTo("sleephq_dashboard");
        assertThat(summary.path("journal_stage_mismatch").asBoolean()).isFalse();
        var reporting = summary.path("minutes_by_stage_for_reporting");
        assertThat(reporting.path("rem").asDouble()).isGreaterThan(150.0);
        assertThat(reporting.path("awake").asDouble()).isLessThan(30.0);
        assertThat(reporting.path("deep").asDouble()).isGreaterThan(100.0);
        assertThat(summary.path("reporting_source").asText()).isEqualTo("sleephq_dashboard_naive");
    }

    @Test
    void summarize_withCpapClip_setsReportingTotalsAndClippedFlag() throws Exception {
        String raw = new String(
                getClass().getResourceAsStream("/journal/2026-05-19-sleep-stages.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var summary = JournalSleepStagesSummary.summarize(
                JournalSleepStagesParser.tryParse(raw),
                java.time.Instant.parse("2026-05-18T22:00:00Z"),
                java.time.Instant.parse("2026-05-19T07:00:00Z"));

        assertThat(summary.path("cpap_session_clipped").asBoolean()).isTrue();
        var reporting = summary.path("minutes_by_stage_for_reporting");
        assertThat(reporting.path("rem").asDouble()).isGreaterThan(60.0);
        assertThat(reporting.path("awake").asDouble()).isLessThan(30.0);
        assertThat(summary.path("reporting_source").asText()).isNotBlank();
    }

    @Test
    void summarize_20260519_mainEpisode_matchesUiBallpark() throws Exception {
        String raw = new String(
                getClass().getResourceAsStream("/journal/2026-05-19-sleep-stages.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var summary = JournalSleepStagesSummary.summarize(JournalSleepStagesParser.tryParse(raw));

        assertThat(summary).isNotNull();
        var episode = summary.path("minutes_by_stage_main_episode");
        assertThat(episode.path("rem").asDouble()).isGreaterThan(60.0);
        assertThat(episode.path("awake").asDouble()).isLessThan(30.0);
        assertThat(summary.path("journal_stage_mismatch").asBoolean()).isTrue();
        assertThat(summary.path("ui_parity_note").asText()).isNotBlank();
        assertThat(summary.path("minutes_by_stage_for_reporting").path("rem").asDouble()).isGreaterThan(60.0);
    }
}
