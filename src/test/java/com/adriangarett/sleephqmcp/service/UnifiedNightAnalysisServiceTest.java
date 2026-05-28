package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.oscar.OscarChannelIds;
import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedNightAnalysisServiceTest {

    @Mock
    private OscarRepository oscarRepository;

    private UnifiedNightAnalysisService service;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new UnifiedNightAnalysisService(oscarRepository);
    }

    @Test
    void analyzeNight_notConfigured_returnsEmpty() {
        when(oscarRepository.isConfigured()).thenReturn(false);
        Optional<ObjectNode> result = service.analyzeNight("2026-05-28");
        assertThat(result).isEmpty();
    }

    @Test
    void analyzeNight_sessionNotFound_returnsEmpty() {
        when(oscarRepository.isConfigured()).thenReturn(true);
        when(oscarRepository.findSessionForDate(LocalDate.parse("2026-05-28")))
                .thenReturn(Optional.empty());
        Optional<ObjectNode> result = service.analyzeNight("2026-05-28");
        assertThat(result).isEmpty();
    }

    @Test
    void analyzeNight_withValidSession_attachesFreshnessAndProvenance() {
        LocalDate date = LocalDate.parse("2026-05-28");
        LocalDate lastSessionDate = LocalDate.now(ZoneId.systemDefault()).minusDays(5); // 5 days ago = acceptable_lag
        Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = start.plusSeconds(3600);

        OscarSessionIndexEntry indexEntry = new OscarSessionIndexEntry(
                123456L, true, true, start, end, List.of(OscarChannelIds.CPAP_AHI), List.of()
        );

        when(oscarRepository.isConfigured()).thenReturn(true);
        when(oscarRepository.findSessionForDate(date)).thenReturn(Optional.of(indexEntry));
        when(oscarRepository.getLastSessionDate()).thenReturn(Optional.of(lastSessionDate));
        
        // Mocks for loading session header / contents
        when(oscarRepository.loadSummaryHeader(indexEntry)).thenReturn(Optional.empty());
        
        // Oscar properties for analysis
        OscarProperties.Analysis analysis = new OscarProperties.Analysis(95, 120, 20, 100, 5);
        OscarProperties properties = new OscarProperties("path", "profile", "device", analysis);
        when(oscarRepository.properties()).thenReturn(properties);

        OscarSession session = new OscarSession(date.toString(), 123456L, start.toEpochMilli(), 3600, new HashMap<>(), List.of());
        when(oscarRepository.loadSession(indexEntry)).thenReturn(Optional.of(session));
        when(oscarRepository.edfPathsForSession(indexEntry)).thenReturn(Optional.empty());

        Optional<ObjectNode> result = service.analyzeNight("2026-05-28");
        assertThat(result).isPresent();
        ObjectNode analysisNode = result.get();

        // Verify Freshness
        assertThat(analysisNode.path("coverage").path("oscar_lag_days").asInt()).isEqualTo(5);
        assertThat(analysisNode.path("coverage").path("oscar_freshness").asText()).isEqualTo("acceptable_lag");

        // Verify Provenance
        assertThat(analysisNode.path("provenance").path("oscar_summaries_xml").path("freshness").asText()).isEqualTo("acceptable_lag");
        assertThat(analysisNode.path("provenance").path("oscar_summaries_xml").path("lag_days").asInt()).isEqualTo(5);
        assertThat(analysisNode.path("provenance").path("oscar_summaries_xml").path("last_session_date").asText()).isEqualTo(lastSessionDate.toString());
    }

    @Test
    void analyzeNight_sessionSpansMidnight_addsEndDateNote() {
        LocalDate date = LocalDate.parse("2026-05-28");
        LocalDate lastSessionDate = LocalDate.now(ZoneId.systemDefault()); // 0 days ago = fresh
        // Session starts on May 27th 11:30 PM, ends on May 28th 6 AM
        Instant start = LocalDate.parse("2026-05-27").atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant();
        Instant end = date.atTime(6, 0).atZone(ZoneId.systemDefault()).toInstant();

        OscarSessionIndexEntry indexEntry = new OscarSessionIndexEntry(
                123456L, true, true, start, end, List.of(OscarChannelIds.CPAP_AHI), List.of()
        );

        when(oscarRepository.isConfigured()).thenReturn(true);
        when(oscarRepository.findSessionForDate(date)).thenReturn(Optional.of(indexEntry));
        when(oscarRepository.getLastSessionDate()).thenReturn(Optional.of(lastSessionDate));
        
        when(oscarRepository.loadSummaryHeader(indexEntry)).thenReturn(Optional.empty());
        
        OscarProperties.Analysis analysis = new OscarProperties.Analysis(95, 120, 20, 100, 5);
        OscarProperties properties = new OscarProperties("path", "profile", "device", analysis);
        when(oscarRepository.properties()).thenReturn(properties);

        OscarSession session = new OscarSession(date.toString(), 123456L, start.toEpochMilli(), 3600, new HashMap<>(), List.of());
        when(oscarRepository.loadSession(indexEntry)).thenReturn(Optional.of(session));
        when(oscarRepository.edfPathsForSession(indexEntry)).thenReturn(Optional.empty());

        Optional<ObjectNode> result = service.analyzeNight("2026-05-28");
        assertThat(result).isPresent();
        ObjectNode analysisNode = result.get();

        // Verify Midnight End-Date Match Note
        assertThat(analysisNode.path("session").path("session_date_note").asText()).isEqualTo("matched_end_date");
    }
}
