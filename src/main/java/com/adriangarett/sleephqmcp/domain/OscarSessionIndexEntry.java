package com.adriangarett.sleephqmcp.domain;

import java.time.Instant;
import java.util.List;

public record OscarSessionIndexEntry(
        long sessionId,
        boolean enabled,
        boolean hasEvents,
        Instant firstInstant,
        Instant lastInstant,
        List<String> channelCodes,
        List<Integer> settingChannelIds
) {
}
