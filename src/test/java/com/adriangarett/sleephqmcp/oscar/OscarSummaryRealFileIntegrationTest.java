package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.support.NightAnalysisSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates summary scan against on-disk OSCAR backups when present.
 */
class OscarSummaryRealFileIntegrationTest {

    private static final Path SUMMARIES_DIR = Path.of(
            System.getProperty("user.home"),
            "Documents/OSCAR_Data/Profiles/adriansian/ResMed_23231819378/Summaries");

    @Test
    @EnabledIf("summariesDirConfigured")
    void loadSession_may18Summary_scansNumericAhiAndChannels() throws Exception {
        byte[] bytes = Files.readAllBytes(SUMMARIES_DIR.resolve("6a09f5b4.000"));
        List<Integer> channelIds = OscarSummaryParser.readAvailableChannelsFromTail(bytes);
        OscarSession parsed = OscarSummaryParser.parse(bytes, LocalDate.parse("2026-05-18"), ZoneId.systemDefault());
        Map<Integer, com.adriangarett.sleephqmcp.domain.ChannelSummary> scanned =
                OscarSummaryChannelStats.extract(bytes, parsed, channelIds);

        assertThat(scanned).isNotEmpty();
        assertThat(scanned.get(OscarChannelIds.CPAP_AHI).avg()).isBetween(0.0, 100.0);

        // TODO(Task 11): channels are now String-keyed in OSCAR 2.0; stub passes empty map
        OscarSession enriched = new OscarSession(
                parsed.date(),
                parsed.sessionId(),
                parsed.startMs(),
                parsed.durationSeconds(),
                Map.of(),
                Map.of());
        ObjectNode indices = NightAnalysisSupport.respiratoryIndices(Optional.of(enriched), null);
        assertThat(indices.has("oscar_ahi_per_hr")).isTrue();
        assertThat(indices.get("oscar_ahi_per_hr").isNumber()).isTrue();
        assertThat(indices.has("see_channels.ahi")).isFalse();
        assertThat(indices.get("ahi_per_hr").asDouble()).isEqualTo(indices.get("oscar_ahi_per_hr").asDouble());
    }

    @Test
    @EnabledIf("summariesDirConfigured")
    void scan_may21Summary_exposesNumericAhi() throws Exception {
        byte[] bytes = Files.readAllBytes(SUMMARIES_DIR.resolve("6a0db26c.000"));
        List<Integer> channelIds = OscarSummaryParser.readAvailableChannelsFromTail(bytes);
        var channels = OscarSummaryChannelStats.scan(bytes, channelIds);
        assertThat(channels.get(OscarChannelIds.CPAP_AHI).avg()).isBetween(0.0, 100.0);
    }

    @Test
    @EnabledIf("summariesDirConfigured")
    void scan_may18Summary_eventCountsNonEmpty() throws Exception {
        byte[] bytes = Files.readAllBytes(SUMMARIES_DIR.resolve("6a09f5b4.000"));
        List<Integer> channelIds = OscarSummaryParser.readAvailableChannelsFromTail(bytes);
        Map<String, Integer> counts = OscarSummaryEventCounts.scan(bytes, channelIds);
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isGreaterThan(0);
    }

    static boolean summariesDirConfigured() {
        return Files.isRegularFile(SUMMARIES_DIR.resolve("6a09f5b4.000"))
                && Files.isRegularFile(SUMMARIES_DIR.resolve("6a0db26c.000"));
    }
}
