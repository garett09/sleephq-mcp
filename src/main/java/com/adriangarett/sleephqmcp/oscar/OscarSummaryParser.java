package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses OSCAR {@code Summaries/*.000} per {@code Session::LoadSummary} (Qt QDataStream LE).
 */
public final class OscarSummaryParser {

    private static final int OSCAR_VARIANT_DOUBLE = 0x87;

    private OscarSummaryParser() {}

    public static OscarSession parse(byte[] bytes, LocalDate calendarDate, ZoneId zone) {
        OscarSummaryHeaderParser.SummaryHeader header = OscarSummaryHeaderParser.parse(bytes);
        List<Integer> available = readAvailableChannelsFromTail(bytes);
        Map<Integer, ChannelSummary> channels = Map.of();
        try {
            channels = parseChannelStats(bytes, available);
        } catch (RuntimeException ignored) {
            // Settings QDataStream layout varies by OSCAR build; EDF stats remain primary.
        }
        String date = calendarDate != null
                ? calendarDate.toString()
                : header.startInstant().atZone(zone).toLocalDate().toString();
        return new OscarSession(
                date,
                header.sessionId(),
                header.startInstant().toEpochMilli(),
                header.durationSeconds(),
                channels,
                available);
    }

    private static Map<Integer, ChannelSummary> parseChannelStats(byte[] bytes, List<Integer> available) {
        OscarBinaryReader reader = new OscarBinaryReader(bytes);
        reader.position(32);
        skipSettingsHash(reader);
        Map<Integer, Double> cnt = readHashDouble(reader);
        Map<Integer, Double> sum = readHashDouble(reader);
        Map<Integer, Double> avg = readHashDouble(reader);
        Map<Integer, Double> wavg = readHashDouble(reader);
        Map<Integer, Double> min = readHashDouble(reader);
        Map<Integer, Double> max = readHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        skipHashDouble(reader);
        return mergeChannelStats(avg, min, max, wavg, cnt, sum, available);
    }

    static List<Integer> readAvailableChannelsFromTail(byte[] bytes) {
        int minOffset = Math.max(32, bytes.length - 256);
        for (int offset = bytes.length - 4; offset >= minOffset; offset -= 4) {
            if (offset + 4 > bytes.length) {
                continue;
            }
            int count = readUInt32At(bytes, offset);
            if (count < 3 || count > 40) {
                continue;
            }
            int dataStart = offset + 4;
            if (dataStart + count * 4L > bytes.length) {
                continue;
            }
            List<Integer> ids = new ArrayList<>(count);
            boolean valid = true;
            for (int i = 0; i < count; i++) {
                int id = readUInt32At(bytes, dataStart + i * 4);
                if (!isPlausibleChannelId(id)) {
                    valid = false;
                    break;
                }
                ids.add(id);
            }
            if (valid) {
                return List.copyOf(ids);
            }
        }
        return List.of();
    }

    private static int readUInt32At(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static boolean isPlausibleChannelId(int id) {
        return (id >= 0x1000 && id <= 0x1028)
                || (id >= 0x1100 && id <= 0x1200)
                || (id >= 0xE200 && id <= 0xE220)
                || (id >= 0x1200 && id <= 0x1203);
    }

    private static void skipSettingsHash(OscarBinaryReader reader) {
        if (reader.remaining() < 4) {
            return;
        }
        int count = reader.readUInt32();
        for (int i = 0; i < count && reader.remaining() >= 8; i++) {
            reader.readUInt32(); // channel id key
            int type = reader.readUInt32();
            skipVariantPayload(reader, type);
        }
    }

    private static void skipVariantPayload(OscarBinaryReader reader, int type) {
        switch (type) {
            case 0 -> { /* invalid / empty */ }
            case 1, 2, 3 -> {
                reader.position(reader.position() + 4);
                if (reader.remaining() > 0 && reader.peekByte() == 0) {
                    reader.position(reader.position() + 1);
                }
            }
            case 6, OSCAR_VARIANT_DOUBLE -> reader.position(reader.position() + 8);
            case 4 -> {
                int len = reader.readUInt32();
                reader.position(reader.position() + len);
            }
            default -> throw new IllegalArgumentException("Unsupported OSCAR settings variant type: " + type);
        }
    }

    private static Map<Integer, Double> readHashDouble(OscarBinaryReader reader) {
        if (reader.remaining() < 4) {
            return Map.of();
        }
        int count = reader.readUInt32();
        Map<Integer, Double> map = new LinkedHashMap<>(count);
        for (int i = 0; i < count && reader.remaining() >= 12; i++) {
            map.put(reader.readUInt32(), reader.readDouble());
        }
        return map;
    }

    private static void skipHashDouble(OscarBinaryReader reader) {
        if (reader.remaining() < 4) {
            return;
        }
        int count = reader.readUInt32();
        reader.position(reader.position() + count * 12);
    }

    private static List<Integer> readChannelList(OscarBinaryReader reader) {
        if (reader.remaining() < 4) {
            return List.of();
        }
        int count = reader.readUInt32();
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count && reader.remaining() >= 4; i++) {
            ids.add(reader.readUInt32());
        }
        return List.copyOf(ids);
    }

    private static Map<Integer, ChannelSummary> mergeChannelStats(
            Map<Integer, Double> avg,
            Map<Integer, Double> min,
            Map<Integer, Double> max,
            Map<Integer, Double> wavg,
            Map<Integer, Double> cnt,
            Map<Integer, Double> sum,
            List<Integer> available) {
        Map<Integer, ChannelSummary> channels = new LinkedHashMap<>();
        for (int id : available) {
            channels.put(id, new ChannelSummary(
                    avg.get(id),
                    min.get(id),
                    max.get(id),
                    wavg.get(id),
                    cnt.get(id),
                    sum.get(id)));
        }
        avg.keySet().forEach(id -> channels.putIfAbsent(id,
                new ChannelSummary(avg.get(id), min.get(id), max.get(id), wavg.get(id), cnt.get(id), sum.get(id))));
        return channels;
    }

    public static OscarSession parseHeaderOnly(byte[] bytes, LocalDate calendarDate, ZoneId zone,
                                              List<Integer> channelIds) {
        OscarSummaryHeaderParser.SummaryHeader header = OscarSummaryHeaderParser.parse(bytes);
        String date = calendarDate != null
                ? calendarDate.toString()
                : header.startInstant().atZone(zone).toLocalDate().toString();
        return new OscarSession(date, header.sessionId(), header.startInstant().toEpochMilli(),
                header.durationSeconds(), Map.of(), channelIds);
    }
}
