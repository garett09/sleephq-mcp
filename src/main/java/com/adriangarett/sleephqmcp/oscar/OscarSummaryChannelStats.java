package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Therapy channel averages from OSCAR {@code Summaries/*.000} {@code m_avg} hash.
 * Used when structured {@link OscarSummaryParser} fails (settings QVariant layout).
 */
public final class OscarSummaryChannelStats {

    private static final int HEADER_BYTES = 32;
    private static final int MIN_HASH_ENTRIES = 8;
    private static final int MAX_HASH_ENTRIES = 40;
    private static final double MAX_AHI_PER_HR = 100.0;
    private static final double MAX_PRESSURE_CM = 40.0;
    private static final double MAX_LEAK_LPM = 200.0;
    private static final double MAX_RESP_RATE = 60.0;

    private static final int[] WAVEFORM_CHANNEL_IDS = {
            OscarChannelIds.CPAP_Pressure,
            OscarChannelIds.CPAP_Leak,
            OscarChannelIds.CPAP_RespRate,
            OscarChannelIds.CPAP_MinuteVent,
            OscarChannelIds.CPAP_MaskPressure,
            OscarChannelIds.CPAP_TidalVolume,
            OscarChannelIds.CPAP_EPAP,
            OscarChannelIds.CPAP_IPAP
    };

    private OscarSummaryChannelStats() {}

    public static Map<Integer, ChannelSummary> extract(
            byte[] summaryBytes,
            OscarSession session,
            List<Integer> availableChannelIds) {
        if (session != null && !session.channels().isEmpty()) {
            return session.channels();
        }
        return scan(summaryBytes, availableChannelIds);
    }

    public static Map<Integer, ChannelSummary> scan(byte[] summaryBytes, List<Integer> availableChannelIds) {
        if (summaryBytes == null || summaryBytes.length < HEADER_BYTES + 16) {
            return Map.of();
        }
        Set<Integer> allowed = buildAllowed(availableChannelIds);
        int scanEnd = Math.max(HEADER_BYTES + 4, summaryBytes.length - 256);
        Map<Integer, Double> bestAvg = Map.of();
        int bestOffset = -1;
        for (int phase = 0; phase < 4; phase++) {
            for (int offset = HEADER_BYTES + phase; offset < scanEnd; offset += 4) {
                Map<Integer, Double> candidate = tryReadAvgHash(summaryBytes, offset, allowed);
                if (!isPlausibleAvgHash(candidate)) {
                    continue;
                }
                if (bestOffset < 0 || offset < bestOffset) {
                    bestOffset = offset;
                    bestAvg = candidate;
                }
            }
        }
        return toChannelSummaries(bestAvg, allowed);
    }

    private static Set<Integer> buildAllowed(List<Integer> availableChannelIds) {
        if (availableChannelIds == null || availableChannelIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(availableChannelIds);
    }

    private static boolean isPlausibleAvgHash(Map<Integer, Double> avg) {
        if (avg.isEmpty()) {
            return false;
        }
        if (OscarSummaryEventCounts.isPlausibleEventCountHashForChannelMap(avg)) {
            return false;
        }
        Double ahi = avg.get(OscarChannelIds.CPAP_AHI);
        if (ahi == null || ahi < 0 || ahi > MAX_AHI_PER_HR) {
            return false;
        }
        int waveformHits = 0;
        for (int waveformId : WAVEFORM_CHANNEL_IDS) {
            Double value = avg.get(waveformId);
            if (value != null && isPlausibleWaveformAvg(waveformId, value)) {
                waveformHits++;
            }
        }
        return waveformHits >= 1;
    }

    private static boolean isPlausibleWaveformAvg(int channelId, double value) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return false;
        }
        return switch (channelId) {
            case OscarChannelIds.CPAP_Pressure, OscarChannelIds.CPAP_MaskPressure,
                    OscarChannelIds.CPAP_EPAP, OscarChannelIds.CPAP_IPAP -> value <= MAX_PRESSURE_CM;
            case OscarChannelIds.CPAP_Leak -> value <= MAX_LEAK_LPM;
            case OscarChannelIds.CPAP_RespRate -> value <= MAX_RESP_RATE;
            case OscarChannelIds.CPAP_MinuteVent -> value <= 50.0;
            case OscarChannelIds.CPAP_TidalVolume -> value <= 2000.0;
            default -> true;
        };
    }

    private static Map<Integer, Double> tryReadAvgHash(byte[] bytes, int offset, Set<Integer> allowed) {
        int entryCount = readUInt32(bytes, offset);
        if (entryCount < MIN_HASH_ENTRIES || entryCount > MAX_HASH_ENTRIES) {
            return Map.of();
        }
        int position = offset + 4;
        Map<Integer, Double> avg = new LinkedHashMap<>();
        for (int i = 0; i < entryCount; i++) {
            if (position + 12 > bytes.length) {
                return Map.of();
            }
            int channelId = readUInt32(bytes, position);
            double value = readDouble(bytes, position + 4);
            position += 12;
            if (!allowed.isEmpty() && !allowed.contains(channelId)) {
                continue;
            }
            if (isFinite(value)) {
                avg.put(channelId, value);
            }
        }
        return avg;
    }

    private static Map<Integer, ChannelSummary> toChannelSummaries(
            Map<Integer, Double> avg, Set<Integer> allowed) {
        if (avg.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ChannelSummary> channels = new LinkedHashMap<>();
        Iterable<Integer> ids = allowed.isEmpty() ? avg.keySet() : allowed;
        for (int id : ids) {
            Double value = avg.get(id);
            if (value != null) {
                channels.put(id, new ChannelSummary(value, null, null, null, null, null));
            }
        }
        avg.forEach((id, value) -> channels.putIfAbsent(id,
                new ChannelSummary(value, null, null, null, null, null)));
        return channels;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static int readUInt32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static double readDouble(byte[] bytes, int offset) {
        return Double.longBitsToDouble(readUInt64(bytes, offset));
    }

    private static long readUInt64(byte[] bytes, int offset) {
        return (readUInt32(bytes, offset) & 0xFFFFFFFFL)
                | ((long) readUInt32(bytes, offset + 4) << 32);
    }
}
