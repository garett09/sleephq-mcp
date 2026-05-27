package com.adriangarett.sleephqmcp.oscar;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code Sessions.info}: magic, version 5, format 2, then (sessionId u32, enabled u8) pairs.
 */
public final class OscarSessionsIndexParser {

    private OscarSessionsIndexParser() {}

    public static List<SessionEntry> parse(byte[] bytes) {
        OscarBinaryReader reader = new OscarBinaryReader(bytes);
        reader.expectMagic();
        int version = reader.readUInt16();
        if (version != OscarConstants.SESSIONS_INFO_VERSION) {
            throw new IllegalArgumentException("Unsupported Sessions.info version: " + version);
        }
        int format = reader.readUInt16();
        if (format != 2) {
            throw new IllegalArgumentException("Unsupported Sessions.info format: " + format);
        }
        int count = reader.readUInt32();
        List<SessionEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (reader.remaining() < 5) {
                break;
            }
            long sessionId = Integer.toUnsignedLong(reader.readUInt32());
            boolean enabled = reader.readUInt8() == 1;
            entries.add(new SessionEntry(sessionId, enabled));
        }
        return entries;
    }

    public record SessionEntry(long sessionId, boolean enabled) {}
}
