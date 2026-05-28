package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalSleepStageAlignmentTest {

    @Mock
    private JournalLookupService journalLookupService;

    @Mock
    private WaveformService waveformService;

    @Test
    void align_remSegment_overlappingSession_returnsStartMinute() {
        String stagesJson = """
                [{"started_at":"2026-05-18T22:14:00Z","ended_at":"2026-05-19T02:14:00Z","stage_type":5}]
                """;
        ObjectNode journalAttrs = JsonApi.mapper().createObjectNode();
        journalAttrs.put("sleep_stages", stagesJson);

        when(journalLookupService.findAttributesByDate(eq("team-1"), eq("2026-05-19")))
                .thenReturn(Optional.of(journalAttrs));
        when(waveformService.loadScanByDate(eq("team-1"), eq("2026-05-19"), isNull()))
                .thenReturn(new ApneaScanResult("BRP.edf", "2026-05-18T22:00:00", 28800, "Flow", 0.15, List.of()));

        Optional<ObjectNode> alignment = JournalSleepStageAlignment.align(
                "team-1", "2026-05-19", "rem", journalLookupService, waveformService, null);

        assertThat(alignment).isPresent();
        assertThat(alignment.get().path("start_minute").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(alignment.get().path("alignment_confidence").asText()).isIn("high", "medium", "low");
    }
}
