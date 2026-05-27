package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OscarTrendServiceTest {

    @Mock
    private OscarRepository oscarRepository;

    @Mock
    private UnifiedNightAnalysisService nightAnalysisService;

    private OscarTrendService trendService;

    @BeforeEach
    void setUp() {
        trendService = new OscarTrendService(oscarRepository, nightAnalysisService);
    }

    @Test
    void trend_withEndDate_dedupesBySessionIdAndPrefersLatestCalendarDate() throws Exception {
        ObjectNode night18 = nightNode("6a09f5b4", "2026-05-18");
        ObjectNode night19 = nightNode("6a0c7c58", "2026-05-19");
        ObjectNode night19Again = nightNode("6a0c7c58", "2026-05-19");
        ObjectNode night21 = nightNode("6a0db26c", "2026-05-21");

        when(nightAnalysisService.analyzeNight(eq("2026-05-15"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-16"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-17"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-18"))).thenReturn(Optional.of(night18));
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.of(night19), Optional.of(night19Again));
        when(nightAnalysisService.analyzeNight(eq("2026-05-20"))).thenReturn(Optional.of(nightNode("6a0db26c", "2026-05-20")));
        when(nightAnalysisService.analyzeNight(eq("2026-05-21"))).thenReturn(Optional.of(night21));

        // Use "full" to preserve calendar_date in the output (summary strips it)
        String json = trendService.trend("2026-05-21", 7, "full");
        JsonNode root = JsonApi.mapper().readTree(json);

        assertThat(root.get("start_date").asText()).isEqualTo("2026-05-15");
        assertThat(root.get("end_date").asText()).isEqualTo("2026-05-21");
        assertThat(root.get("nights")).hasSize(3);
        assertThat(root.get("nights").get(0).get("calendar_date").asText()).isEqualTo("2026-05-18");
        assertThat(root.get("nights").get(1).get("calendar_date").asText()).isEqualTo("2026-05-19");
        assertThat(root.get("nights").get(2).get("calendar_date").asText()).isEqualTo("2026-05-21");
    }

    @Test
    void summaryDetailEmitsSlimRows() throws Exception {
        ObjectNode night = richNightNode("abc123", "2026-05-21");
        when(nightAnalysisService.analyzeNight(eq("2026-05-15"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-16"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-17"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-18"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-20"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-21"))).thenReturn(Optional.of(night));

        // default (no detail arg) uses summary
        String json = trendService.trend("2026-05-21", 7);
        JsonNode root = JsonApi.mapper().readTree(json);
        JsonNode firstNight = root.get("nights").get(0);

        // slim fields present
        assertThat(firstNight.has("date")).isTrue();
        assertThat(firstNight.has("session")).isTrue();
        assertThat(firstNight.has("respiratory_indices")).isTrue();
        assertThat(firstNight.has("events")).isTrue();
        assertThat(firstNight.has("therapy")).isTrue();

        // stripped fields absent
        assertThat(firstNight.has("calendar_date")).isFalse();
        assertThat(firstNight.has("notable_moments")).isFalse();
        assertThat(firstNight.has("data_sources")).isFalse();
        assertThat(firstNight.has("coverage")).isFalse();
        assertThat(firstNight.has("channels")).isFalse();

        // events has counts but no timed_sample
        JsonNode events = firstNight.get("events");
        assertThat(events.has("counts")).isTrue();
        assertThat(events.has("timed_sample")).isFalse();
        assertThat(events.has("timed_sample_truncated")).isFalse();

        // therapy has pressure from channels
        JsonNode therapy = firstNight.get("therapy");
        assertThat(therapy.has("pressure")).isTrue();
    }

    @Test
    void fullDetailKeepsExistingShape() throws Exception {
        ObjectNode night = richNightNode("abc123", "2026-05-21");
        when(nightAnalysisService.analyzeNight(eq("2026-05-15"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-16"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-17"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-18"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-20"))).thenReturn(Optional.empty());
        when(nightAnalysisService.analyzeNight(eq("2026-05-21"))).thenReturn(Optional.of(night));

        String json = trendService.trend("2026-05-21", 7, "full");
        JsonNode root = JsonApi.mapper().readTree(json);
        JsonNode firstNight = root.get("nights").get(0);

        assertThat(firstNight.has("notable_moments")).isTrue();
        assertThat(firstNight.has("data_sources")).isTrue();
        assertThat(firstNight.has("channels")).isTrue();
        assertThat(firstNight.has("calendar_date")).isTrue();
    }

    @Test
    void rootIncludesDetailField() throws Exception {
        when(nightAnalysisService.analyzeNight(eq("2026-05-21"))).thenReturn(Optional.empty());

        String summaryJson = trendService.trend("2026-05-21", 1);
        assertThat(JsonApi.mapper().readTree(summaryJson).get("detail").asText()).isEqualTo("summary");

        when(nightAnalysisService.analyzeNight(eq("2026-05-21"))).thenReturn(Optional.empty());
        String fullJson = trendService.trend("2026-05-21", 1, "full");
        assertThat(JsonApi.mapper().readTree(fullJson).get("detail").asText()).isEqualTo("full");
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    /** Minimal node used by the dedup test (full shape — no slim behaviour expected). */
    private static ObjectNode nightNode(String sessionId, String calendarDate) {
        ObjectNode night = JsonApi.mapper().createObjectNode();
        night.put("calendar_date", calendarDate);
        night.put("date", calendarDate);
        night.putObject("session").put("session_id", sessionId);
        return night;
    }

    /**
     * Rich fixture that mirrors the shape produced by {@link UnifiedNightAnalysisService}:
     * date, calendar_date, session, respiratory_indices, events (with counts + timed_sample),
     * channels (with pressure), data_sources, coverage, notable_moments.
     */
    private static ObjectNode richNightNode(String sessionId, String calendarDate) {
        ObjectNode night = JsonApi.mapper().createObjectNode();
        night.put("date", calendarDate);
        night.put("calendar_date", calendarDate);
        night.putObject("session").put("session_id", sessionId);
        night.putObject("respiratory_indices").put("ahi", 2.5);

        ObjectNode events = night.putObject("events");
        events.putObject("counts").put("H", 3);
        events.putArray("timed_sample").addObject().put("t", "00:01:00");
        events.put("timed_sample_truncated", false);
        events.put("eve_total", 3);

        ObjectNode channels = night.putObject("channels");
        channels.putObject("pressure").put("mean", 8.5);
        channels.putObject("leak").put("mean", 1.2);
        channels.putObject("ahi").put("mean", 2.5);

        night.putArray("data_sources").add("oscar_summaries_xml");
        night.putObject("coverage").put("has_sleephq", false);
        night.putArray("notable_moments").addObject().put("type", "peak_leak");
        return night;
    }
}
