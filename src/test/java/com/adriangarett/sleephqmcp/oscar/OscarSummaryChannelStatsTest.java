package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OscarSummaryChannelStatsTest {

    private static final int MIN_HASH_ENTRIES = 8;

    @Test
    void scan_findsAvgHashInSyntheticSummary() {
        byte[] bytes = buildSummaryWithAvgHash(32, Map.of(
                OscarChannelIds.CPAP_AHI, 1.24,
                OscarChannelIds.CPAP_Pressure, 10.5,
                OscarChannelIds.CPAP_Leak, 2.0));
        Map<Integer, ChannelSummary> channels = OscarSummaryChannelStats.scan(bytes, List.of());
        assertThat(channels.get(OscarChannelIds.CPAP_AHI).avg()).isEqualTo(1.24);
        assertThat(channels.get(OscarChannelIds.CPAP_Pressure).avg()).isEqualTo(10.5);
    }

    @Test
    void scan_skipsEventCountHashAndFindsAvgHashLater() {
        byte[] bytes = new byte[512];
        writeHash(bytes, 32, Map.of(
                OscarChannelIds.CPAP_ClearAirway, 3.0,
                OscarChannelIds.CPAP_Obstructive, 0.0,
                OscarChannelIds.CPAP_Hypopnea, 2.0,
                OscarChannelIds.CPAP_Apnea, 0.0));
        writeHash(bytes, 200, Map.of(
                OscarChannelIds.CPAP_AHI, 1.0,
                OscarChannelIds.CPAP_Pressure, 11.0,
                OscarChannelIds.CPAP_Leak, 2.0));
        Map<Integer, ChannelSummary> channels = OscarSummaryChannelStats.scan(bytes, List.of());
        assertThat(channels).doesNotContainKey(OscarChannelIds.CPAP_ClearAirway);
        assertThat(channels.get(OscarChannelIds.CPAP_AHI).avg()).isEqualTo(1.0);
    }

    @Test
    @EnabledIf("realSummaryMay21Configured")
    void scan_realResMedSummary_exposesNumericAhi() throws Exception {
        byte[] bytes = Files.readAllBytes(realSummaryPath("6a0db26c.000"));
        List<Integer> channelIds = OscarSummaryParser.readAvailableChannelsFromTail(bytes);
        Map<Integer, ChannelSummary> channels = OscarSummaryChannelStats.scan(bytes, channelIds);
        assertThat(channels.get(OscarChannelIds.CPAP_AHI).avg()).isNotNull();
        assertThat(channels.get(OscarChannelIds.CPAP_AHI).avg()).isBetween(0.0, 100.0);
    }

    @Test
    @EnabledIf("realSummaryMay18Configured")
    void scan_realMay18Summary_exposesNumericAhi() throws Exception {
        byte[] bytes = Files.readAllBytes(realSummaryPath("6a09f5b4.000"));
        List<Integer> channelIds = OscarSummaryParser.readAvailableChannelsFromTail(bytes);
        Map<Integer, ChannelSummary> channels = OscarSummaryChannelStats.scan(bytes, channelIds);
        assertThat(channels.get(OscarChannelIds.CPAP_AHI).avg()).isBetween(0.0, 100.0);
    }

    static boolean realSummaryMay21Configured() {
        return Files.isRegularFile(realSummaryPath("6a0db26c.000"));
    }

    static boolean realSummaryMay18Configured() {
        return Files.isRegularFile(realSummaryPath("6a09f5b4.000"));
    }

    private static Path realSummaryPath(String fileName) {
        String env = System.getenv("OSCAR_SUMMARY_DUMP_PATH");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(
                System.getProperty("user.home"),
                "Documents/OSCAR_Data/Profiles/adriansian/ResMed_23231819378/Summaries",
                fileName);
    }

    private static byte[] buildSummaryWithAvgHash(int hashOffset, Map<Integer, Double> avgs) {
        byte[] bytes = new byte[Math.max(512, hashOffset + 4 + MIN_HASH_ENTRIES * 12 + 64)];
        writeHash(bytes, hashOffset, avgs);
        return bytes;
    }

    private static void writeHash(byte[] bytes, int hashOffset, Map<Integer, Double> values) {
        int entryCount = Math.max(values.size() + 5, MIN_HASH_ENTRIES);
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(hashOffset);
        buf.putInt(entryCount);
        values.forEach((channelId, value) -> {
            buf.putInt(channelId);
            buf.putDouble(value);
        });
        for (int i = values.size(); i < entryCount; i++) {
            buf.putInt(0x1100 + i);
            buf.putDouble(1.0);
        }
    }
}
