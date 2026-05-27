package com.adriangarett.sleephqmcp.oscar;

import java.time.Instant;

/**
 * Parses the 32-byte OSCAR summary (.{@code 000}) file header.
 * {@code s_last} is either session duration in seconds (legacy/test fixtures) or end epoch milliseconds
 * when {@code s_last > s_first} and {@code s_last} looks like epoch ms.
 */
public final class OscarSummaryHeaderParser {

    /** Values at or above this are treated as epoch milliseconds, not duration seconds. */
    private static final long EPOCH_MS_THRESHOLD = 1_000_000_000_000L;

    private OscarSummaryHeaderParser() {}

    public static SummaryHeader parse(byte[] bytes) {
        if (bytes.length < 32) {
            throw new IllegalArgumentException("Summary file too short: " + bytes.length);
        }
        OscarBinaryReader reader = new OscarBinaryReader(bytes);
        reader.expectMagic();
        int version = reader.readUInt16();
        int fileType = reader.readUInt16();
        long machineId = Integer.toUnsignedLong(reader.readUInt32());
        long sessionId = Integer.toUnsignedLong(reader.readUInt32());
        long firstMs = reader.readInt64();
        long lastField = reader.readInt64();
        long durationSeconds = resolveDurationSeconds(firstMs, lastField);
        return new SummaryHeader(version, fileType, machineId, sessionId,
                Instant.ofEpochMilli(firstMs), durationSeconds);
    }

    static long resolveDurationSeconds(long firstMs, long lastField) {
        if (lastField > firstMs && lastField >= EPOCH_MS_THRESHOLD) {
            return (lastField - firstMs) / 1000L;
        }
        return lastField;
    }

    public record SummaryHeader(
            int version,
            int fileType,
            long machineId,
            long sessionId,
            Instant startInstant,
            long durationSeconds
    ) {}
}
