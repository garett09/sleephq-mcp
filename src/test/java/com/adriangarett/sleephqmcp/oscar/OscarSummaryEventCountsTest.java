package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OscarSummaryEventCountsTest {

    private static final int MIN_HASH_ENTRIES_FOR_TEST = 8;

    @Test
    void scan_acceptsModerateApneaEventTotals() {
        byte[] bytes = buildSummaryWithCntHash(32, Map.of(
                OscarChannelIds.CPAP_Obstructive, 45,
                OscarChannelIds.CPAP_Hypopnea, 30,
                OscarChannelIds.CPAP_ClearAirway, 5,
                OscarChannelIds.CPAP_Apnea, 2));
        Map<String, Integer> counts = OscarSummaryEventCounts.scan(bytes, List.of());
        assertThat(counts).containsEntry("obstructive", 45);
        assertThat(counts).containsEntry("hypopnea", 30);
        assertThat(counts).containsEntry("clear_airway", 5);
        assertThat(counts).containsEntry("apnea", 2);
    }

    @Test
    void scan_findsCntHashInSyntheticSummary() {
        byte[] bytes = buildSummaryWithCntHash(32, Map.of(
                OscarChannelIds.CPAP_ClearAirway, 3,
                OscarChannelIds.CPAP_Obstructive, 0,
                OscarChannelIds.CPAP_Hypopnea, 2));
        Map<String, Integer> counts = OscarSummaryEventCounts.scan(bytes, List.of());
        assertThat(counts).containsEntry("clear_airway", 3);
        assertThat(counts).containsEntry("obstructive", 0);
        assertThat(counts).containsEntry("hypopnea", 2);
    }

    @Test
    @EnabledIf("realSummaryConfigured")
    void scan_realResMedSummary_matchesOscarDashboardOrderOfMagnitude() throws Exception {
        byte[] bytes = Files.readAllBytes(realSummaryPath());
        List<Integer> channels = OscarSummaryParser.readAvailableChannelsFromTail(bytes);
        Map<String, Integer> counts = OscarSummaryEventCounts.scan(bytes, channels);
        assertThat(counts).containsEntry("clear_airway", 3);
        assertThat(counts).containsEntry("hypopnea", 2);
        assertThat(counts).containsEntry("obstructive", 0);
        assertThat(counts).containsEntry("apnea", 0);
        assertThat(counts.keySet()).noneMatch(key -> key.startsWith("ch_"));
    }

    @Test
    void buildSummary_mergesSummaryCounts() {
        var eve = new com.adriangarett.sleephqmcp.domain.DeviceEventResult(
                "EVE.edf", "2026-05-20T21:00:00", 3600, "device_eve", List.of());
        var summary = Map.of("clear_airway", 3, "hypopnea", 2);
        var node = OscarEventSummaryBuilder.buildSummary(eve, 100, java.util.Optional.of(summary));
        assertThat(node.get("eve_total").asInt()).isZero();
        assertThat(node.get("summary_counts").get("clear_airway").asInt()).isEqualTo(3);
        assertThat(node.get("summary_total").asInt()).isEqualTo(5);
    }

    static boolean realSummaryConfigured() {
        return Files.isRegularFile(realSummaryPath());
    }

    private static Path realSummaryPath() {
        String env = System.getenv("OSCAR_SUMMARY_DUMP_PATH");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(
                System.getProperty("user.home"),
                "Documents/OSCAR_Data/Profiles/adriansian/ResMed_23231819378/Summaries/6a0db26c.000");
    }

    private static byte[] buildSummaryWithCntHash(int hashOffset, Map<Integer, Integer> eventCounts) {
        int entryCount = Math.max(eventCounts.size() + 5, MIN_HASH_ENTRIES_FOR_TEST);
        int size = Math.max(512, hashOffset + 4 + entryCount * 12 + 64);
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(hashOffset);
        buf.putInt(entryCount);
        eventCounts.forEach((channelId, count) -> {
            buf.putInt(channelId);
            buf.putDouble(count);
        });
        for (int i = eventCounts.size(); i < entryCount; i++) {
            buf.putInt(0x1100 + i);
            buf.putDouble(1.0);
        }
        byte[] bytes = new byte[size];
        buf.rewind();
        buf.get(bytes);
        return bytes;
    }
}
