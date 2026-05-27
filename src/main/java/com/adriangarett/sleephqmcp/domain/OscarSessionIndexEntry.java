package com.adriangarett.sleephqmcp.domain;

import java.time.Instant;
import java.util.List;

public record OscarSessionIndexEntry(
        long sessionId,
        boolean enabled,
        boolean hasEvents,
        Instant firstInstant,
        Instant lastInstant,
        List<Integer> channelIds,
        List<Integer> settingChannelIds
) {
    public String summaryFileName() {
        return Long.toHexString(sessionId) + ".000";
    }
}
