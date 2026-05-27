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

        String json = trendService.trend("2026-05-21", 7);
        JsonNode root = JsonApi.mapper().readTree(json);

        assertThat(root.get("start_date").asText()).isEqualTo("2026-05-15");
        assertThat(root.get("end_date").asText()).isEqualTo("2026-05-21");
        assertThat(root.get("nights")).hasSize(3);
        assertThat(root.get("nights").get(0).get("calendar_date").asText()).isEqualTo("2026-05-18");
        assertThat(root.get("nights").get(1).get("calendar_date").asText()).isEqualTo("2026-05-19");
        assertThat(root.get("nights").get(2).get("calendar_date").asText()).isEqualTo("2026-05-21");
    }

    private static ObjectNode nightNode(String sessionId, String calendarDate) {
        ObjectNode night = JsonApi.mapper().createObjectNode();
        night.put("calendar_date", calendarDate);
        night.putObject("session").put("session_id", sessionId);
        return night;
    }
}
