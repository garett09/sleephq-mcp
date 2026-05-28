package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.oscar.OscarChannelIds;
import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.Map;
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
    void analyzeNight_withValidSession_attachesExportFreshnessAndProvenance() {
        LocalDate date = LocalDate.parse("2026-05-28");
        LocalDate lastSessionDate = LocalDate.now(ZoneId.systemDefault()).minusDays(5);
        Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = start.plusSeconds(3600);

        OscarSessionIndexEntry indexEntry = sessionEntry(start, end);
        stubConfiguredSession(date, indexEntry, lastSessionDate, emptySession(date, start));

        Optional<ObjectNode> result = service.analyzeNight("2026-05-28");
        assertThat(result).isPresent();
        ObjectNode analysisNode = result.get();

        assertThat(analysisNode.path("coverage").path("oscar_export_lag_days").asInt()).isEqualTo(5);
        assertThat(analysisNode.path("coverage").path("oscar_export_freshness").asText()).isEqualTo("acceptable_lag");

        ObjectNode oscarProv = (ObjectNode) analysisNode.path("provenance").path("oscar_summaries_xml");
        assertThat(oscarProv.path("export_freshness").asText()).isEqualTo("acceptable_lag");
        assertThat(oscarProv.path("export_lag_days").asInt()).isEqualTo(5);
        assertThat(oscarProv.path("freshness_scope").asText()).isEqualTo("export");
        assertThat(oscarProv.path("last_session_date").asText()).isEqualTo(lastSessionDate.toString());
    }

    @Test
    void analyzeNight_sessionSpansMidnight_addsEndDateNote() {
        LocalDate date = LocalDate.parse("2026-05-28");
        LocalDate lastSessionDate = LocalDate.now(ZoneId.systemDefault());
        Instant start = LocalDate.parse("2026-05-27").atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant();
        Instant end = date.atTime(6, 0).atZone(ZoneId.systemDefault()).toInstant();

        OscarSessionIndexEntry indexEntry = sessionEntry(start, end);
        stubConfiguredSession(date, indexEntry, lastSessionDate, emptySession(date, start));

        Optional<ObjectNode> result = service.analyzeNight("2026-05-28");
        assertThat(result).isPresent();
        assertThat(result.get().path("session").path("session_date_note").asText()).isEqualTo("matched_end_date");
    }

    @Test
    void analyzeNight_sessionSameCalendarDay_omitsEndDateNote() {
        LocalDate date = LocalDate.parse("2026-05-28");
        LocalDate lastSessionDate = LocalDate.now(ZoneId.systemDefault());
        Instant start = date.atTime(22, 0).atZone(ZoneId.systemDefault()).toInstant();
        Instant end = date.atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant();

        OscarSessionIndexEntry indexEntry = sessionEntry(start, end);
        stubConfiguredSession(date, indexEntry, lastSessionDate, emptySession(date, start));

        Optional<ObjectNode> result = service.analyzeNight("2026-05-28");
        assertThat(result).isPresent();
        assertThat(result.get().path("session").has("session_date_note")).isFalse();
    }

    @Test
    void analyzeNight_withMachineAttrs_attachesDataConflicts() {
        LocalDate date = LocalDate.parse("2026-05-28");
        LocalDate lastSessionDate = LocalDate.now(ZoneId.systemDefault());
        Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = start.plusSeconds(3600);

        ObjectNode machineAttrs = mapper.createObjectNode();
        machineAttrs.putObject("ahi_summary").put("av", 1.2);

        Map<Integer, ChannelSummary> channels = new HashMap<>();
        channels.put(OscarChannelIds.CPAP_AHI, new ChannelSummary(3.5, 0.0, 10.0, 3.5, null, null));
        OscarSession session = new OscarSession(
                date.toString(),
                123456L,
                start.toEpochMilli(),
                3600,
                channels,
                List.of(OscarChannelIds.CPAP_AHI));

        OscarSessionIndexEntry indexEntry = sessionEntry(start, end);
        stubConfiguredSession(date, indexEntry, lastSessionDate, session);

        Optional<ObjectNode> result = service.analyzeNight("2026-05-28", machineAttrs, null);
        assertThat(result).isPresent();

        ArrayNode conflicts = (ArrayNode) result.get().path("data_conflicts");
        assertThat(conflicts).isNotEmpty();
        ObjectNode ahiConflict = findConflictByMetric(conflicts, "ahi");
        assertThat(ahiConflict).isNotNull();
        assertThat(ahiConflict.path("severity").asText()).isEqualTo("critical");
        assertThat(ahiConflict.path("sleephq_value").asDouble()).isEqualTo(1.2);
        assertThat(ahiConflict.path("oscar_value").asDouble()).isEqualTo(3.5);
    }

    @Test
    void analyzeNight_withJournalAttrs_addsJournalProvenance() {
        LocalDate date = LocalDate.parse("2026-05-28");
        LocalDate lastSessionDate = LocalDate.now(ZoneId.systemDefault());
        Instant start = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = start.plusSeconds(3600);

        ObjectNode journalAttrs = mapper.createObjectNode();
        journalAttrs.put("feeling_score", 4);

        OscarSessionIndexEntry indexEntry = sessionEntry(start, end);
        stubConfiguredSession(date, indexEntry, lastSessionDate, emptySession(date, start));

        Optional<ObjectNode> result = service.analyzeNight("2026-05-28", null, journalAttrs);
        assertThat(result).isPresent();
        assertThat(result.get().path("provenance").path("sleephq_journal").path("available").asBoolean()).isTrue();
    }

    private void stubConfiguredSession(
            LocalDate date,
            OscarSessionIndexEntry indexEntry,
            LocalDate lastSessionDate,
            OscarSession session) {
        when(oscarRepository.isConfigured()).thenReturn(true);
        when(oscarRepository.findSessionForDate(date)).thenReturn(Optional.of(indexEntry));
        when(oscarRepository.getLastSessionDate()).thenReturn(Optional.of(lastSessionDate));
        when(oscarRepository.loadSummaryHeader(indexEntry)).thenReturn(Optional.empty());

        OscarProperties.Analysis analysis = new OscarProperties.Analysis(95, 120, 20, 100, 5);
        OscarProperties properties = new OscarProperties("path", "profile", "device", analysis);
        when(oscarRepository.properties()).thenReturn(properties);
        when(oscarRepository.loadSession(indexEntry)).thenReturn(Optional.of(session));
        when(oscarRepository.edfPathsForSession(indexEntry)).thenReturn(Optional.empty());
    }

    private static OscarSessionIndexEntry sessionEntry(Instant start, Instant end) {
        return new OscarSessionIndexEntry(
                123456L, true, true, start, end, List.of(OscarChannelIds.CPAP_AHI), List.of());
    }

    private static OscarSession emptySession(LocalDate date, Instant start) {
        return new OscarSession(date.toString(), 123456L, start.toEpochMilli(), 3600, new HashMap<>(), List.of());
    }

    private static ObjectNode findConflictByMetric(ArrayNode conflicts, String metric) {
        for (int i = 0; i < conflicts.size(); i++) {
            ObjectNode conflict = (ObjectNode) conflicts.get(i);
            if (conflict.path("metric").asText().equals(metric)) {
                return conflict;
            }
        }
        return null;
    }
}
