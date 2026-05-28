package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Event totals from OSCAR {@code Summaries/*.000} {@code m_cnt} hash (OSCAR dashboard counts).
 * Complements {@code EVE.edf} timed flags, which may omit summary-only channels (e.g. clear airway).
 */
public final class OscarSummaryEventCounts {

    private static final int HEADER_BYTES = 32;
    private static final int MIN_HASH_ENTRIES = 8;
    private static final int MAX_HASH_ENTRIES = 40;
    private static final double MAX_EVENT_COUNT = 10_000;
    /** Per-night event totals above this are almost certainly a misaligned hash parse. */
    private static final int MAX_PLAUSIBLE_NIGHTLY_EVENT_COUNT = 500;

    private OscarSummaryEventCounts() {}

    public static Map<String, Integer> fromSession(OscarSession session) {
        if (session == null || session.channels().isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<Integer, ChannelSummary> entry : session.channels().entrySet()) {
            if (!OscarChannelIdClassification.isCountedEventChannel(entry.getKey())) {
                continue;
            }
            Integer count = toIntegerCount(entry.getValue().count());
            if (count != null) {
                counts.put(OscarChannelCatalog.fieldName(entry.getKey()), count);
            }
        }
        return counts;
    }

    public static Map<String, Integer> scan(byte[] summaryBytes, List<Integer> availableChannelIds) {
        if (summaryBytes == null || summaryBytes.length < HEADER_BYTES + 16) {
            return Map.of();
        }
        Set<Integer> allowed = buildAllowed(availableChannelIds);
        int scanEnd = Math.max(HEADER_BYTES + 4, summaryBytes.length - 256);
        Map<Integer, Integer> best = Map.of();
        int bestOffset = -1;
        // m_cnt hash offset is not always 32-aligned (e.g. ResMed 6a0db26c.000 at byte 243).
        for (int phase = 0; phase < 4; phase++) {
            for (int offset = HEADER_BYTES + phase; offset < scanEnd; offset += 4) {
                Map<Integer, Integer> candidate = tryReadCountHash(summaryBytes, offset, allowed);
                if (!isPlausibleEventCountHash(candidate)) {
                    continue;
                }
                if (bestOffset < 0 || offset < bestOffset) {
                    bestOffset = offset;
                    best = candidate;
                }
            }
        }
        return toFieldMap(best);
    }

    public static Map<String, Integer> extract(
            byte[] summaryBytes,
            OscarSession session,
            List<Integer> availableChannelIds) {
        Map<String, Integer> fromSession = session != null ? fromSession(session) : Map.of();
        if (!fromSession.isEmpty()) {
            return fromSession;
        }
        return scan(summaryBytes, availableChannelIds);
    }

    private static Set<Integer> buildAllowed(List<Integer> availableChannelIds) {
        if (availableChannelIds == null || availableChannelIds.isEmpty()) {
            return OscarChannelIdClassification.countedEventChannelIds();
        }
        return Set.copyOf(availableChannelIds);
    }

    /**
     * Package-visible for {@link OscarSummaryChannelStats} to skip {@code m_cnt} offsets when scanning {@code m_avg}.
     */
    static boolean isPlausibleEventCountHashForChannelMap(Map<Integer, Double> values) {
        if (values.isEmpty()) {
            return false;
        }
        Map<Integer, Integer> asCounts = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> entry : values.entrySet()) {
            Integer count = toIntegerCount(entry.getValue());
            if (count != null) {
                asCounts.put(entry.getKey(), count);
            }
        }
        return isPlausibleEventCountHash(asCounts);
    }

    private static boolean isPlausibleEventCountHash(Map<Integer, Integer> counts) {
        if (counts.isEmpty()) {
            return false;
        }
        int corePresent = 0;
        for (int coreId : new int[] {
            OscarChannelIds.CPAP_ClearAirway,
            OscarChannelIds.CPAP_Obstructive,
            OscarChannelIds.CPAP_Hypopnea,
            OscarChannelIds.CPAP_Apnea
        }) {
            if (counts.containsKey(coreId)) {
                corePresent++;
            }
        }
        if (corePresent < 3) {
            return false;
        }
        for (int count : counts.values()) {
            if (count > MAX_PLAUSIBLE_NIGHTLY_EVENT_COUNT) {
                return false;
            }
        }
        return true;
    }

    private static Map<Integer, Integer> tryReadCountHash(byte[] bytes, int offset, Set<Integer> allowed) {
        int entryCount = readUInt32(bytes, offset);
        if (entryCount < MIN_HASH_ENTRIES || entryCount > MAX_HASH_ENTRIES) {
            return Map.of();
        }
        int position = offset + 4;
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < entryCount; i++) {
            if (position + 12 > bytes.length) {
                return Map.of();
            }
            int channelId = readUInt32(bytes, position);
            double value = readDouble(bytes, position + 4);
            position += 12;
            if (!allowed.contains(channelId) || !OscarChannelIdClassification.isCountedEventChannel(channelId)) {
                continue;
            }
            Integer count = toIntegerCount(value);
            if (count != null) {
                counts.put(channelId, count);
            }
        }
        return counts;
    }

    private static Map<String, Integer> toFieldMap(Map<Integer, Integer> byChannelId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        byChannelId.forEach((id, count) -> out.put(OscarChannelCatalog.fieldName(id), count));
        return out;
    }

    private static Integer toIntegerCount(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        if (value < 0 || value > MAX_EVENT_COUNT) {
            return null;
        }
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) > 0.01) {
            return null;
        }
        return (int) rounded;
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
