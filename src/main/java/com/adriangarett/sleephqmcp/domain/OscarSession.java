package com.adriangarett.sleephqmcp.domain;

import java.util.List;
import java.util.Map;

public record OscarSession(
        String date,
        long sessionId,
        long startMs,
        long durationSeconds,
        Map<Integer, ChannelSummary> channels,
        List<Integer> availableChannelIds
) {
    public long endMs() {
        return startMs + durationSeconds * 1000L;
    }
}
