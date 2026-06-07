package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OscarRepositoryTest {

    private OscarRepository repo;
    private Connection conn;

    private static OscarProperties props() {
        return new OscarProperties(true, "", "", "", new OscarProperties.Analysis(95, 120, 20, 100, 5));
    }

    @BeforeEach
    void setUp() throws Exception {
        conn = OscarSqliteFixture.createInMemory();
        repo = OscarRepository.forTesting(props(), OscarSqliteDb.wrap(conn));
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void isReachableWithFixtureData() {
        assertThat(repo.isReachable()).isTrue();
    }

    @Test
    void isConfiguredChecksEnabledFlag() {
        OscarRepository disabled = OscarRepository.forTesting(
            new OscarProperties(false, "", "", "", new OscarProperties.Analysis(95, 120, 20, 100, 5)),
            OscarSqliteDb.wrap(conn));
        assertThat(disabled.isReachable()).isFalse();
    }

    @Test
    void getLastSessionDateReturnsFixtureDate() {
        Optional<LocalDate> date = repo.getLastSessionDate();
        assertThat(date).hasValue(LocalDate.of(2024, 1, 15));
    }

    @Test
    void findSessionForDateReturnsEntry() {
        Optional<OscarSessionIndexEntry> entry = repo.findSessionForDate(LocalDate.of(2024, 1, 15));
        assertThat(entry).isPresent();
        assertThat(entry.get().channelCodes()).contains("RespRate", "Pressure");
        assertThat(entry.get().enabled()).isTrue();
        assertThat(entry.get().hasEvents()).isTrue();
    }

    @Test
    void findSessionForDateMissingDateReturnsEmpty() {
        assertThat(repo.findSessionForDate(LocalDate.of(2023, 1, 1))).isEmpty();
    }

    @Test
    void findSessionsInRangeReturnsSingleDateEntry() {
        List<OscarSessionIndexEntry> entries = repo.findSessionsInRange(
            LocalDate.of(2024, 1, 14), LocalDate.of(2024, 1, 16));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).channelCodes()).isNotEmpty();
    }

    @Test
    void loadSessionReturnsChannelSummaries() {
        Optional<OscarSession> session = repo.loadSession(LocalDate.of(2024, 1, 15));
        assertThat(session).isPresent();
        assertThat(session.get().channels()).containsKeys("RespRate", "Pressure");
        assertThat(session.get().channels().get("RespRate").avg()).isGreaterThan(0);
    }

    @Test
    void loadSessionReturnsEventCounts() {
        Optional<OscarSession> session = repo.loadSession(LocalDate.of(2024, 1, 15));
        assertThat(session).isPresent();
        Map<String, Integer> events = session.get().eventCounts();
        assertThat(events.getOrDefault("clear_airway", 0)).isEqualTo(2);
        assertThat(events.getOrDefault("hypopnea", 0)).isEqualTo(1);
    }

    @Test
    void loadSessionViaIndexEntry() {
        OscarSessionIndexEntry entry = repo.findSessionForDate(LocalDate.of(2024, 1, 15)).orElseThrow();
        Optional<OscarSession> session = repo.loadSession(entry);
        assertThat(session).isPresent();
    }

    @Test
    void loadSummaryHeaderReturnsPressureSetting() {
        OscarSessionIndexEntry entry = repo.findSessionForDate(LocalDate.of(2024, 1, 15)).orElseThrow();
        Optional<Map<String, Double>> header = repo.loadSummaryHeader(entry);
        assertThat(header).isPresent();
        assertThat(header.get()).containsKey("Pressure");
        assertThat(header.get().get("Pressure")).isEqualTo(10.6);
    }

    @Test
    void loadChannelHistogramsReturnsMergedBuckets() {
        Map<String, OscarChannelHistogram> histograms = repo.loadChannelHistograms(LocalDate.of(2024, 1, 15));
        assertThat(histograms).containsKey("RespRate");
        OscarChannelHistogram hist = histograms.get("RespRate");
        assertThat(hist.gainFactor()).isEqualTo(0.2);
        assertThat(hist.buckets()).isNotEmpty();
        // Both sessions: session1 has value=80→50,100→50; session2 has value=85→30,110→30
        assertThat(hist.buckets().get(80)).isEqualTo(50L);
        assertThat(hist.buckets().get(85)).isEqualTo(30L);
    }

    @Test
    void loadSummaryEventCountsReturnsCounts() {
        Optional<Map<String, Integer>> counts = repo.loadSummaryEventCounts(LocalDate.of(2024, 1, 15));
        assertThat(counts).isPresent();
        assertThat(counts.get().get("clear_airway")).isEqualTo(2);
        assertThat(counts.get().get("hypopnea")).isEqualTo(1);
    }

    @Test
    void edfMethodsReturnEmpty() {
        assertThat(repo.edfPathsForDate(LocalDate.of(2024, 1, 15))).isEmpty();
        assertThat(repo.loadEventsByDate(LocalDate.of(2024, 1, 15))).isEmpty();
        assertThat(repo.loadPldWaveform(LocalDate.of(2024, 1, 15))).isEmpty();
    }
}
