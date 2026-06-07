package com.adriangarett.sleephqmcp.domain;

import java.util.List;
import java.util.Map;

public record OscarSession(
        String date,
        long sessionId,
        long startMs,
        long durationSeconds,
        Map<String, ChannelSummary> channels,
        Map<String, Integer> eventCounts
) {
    public long endMs() {
        return startMs + durationSeconds * 1000L;
    }

    public List<String> availableChannelCodes() {
        return List.copyOf(channels.keySet());
    }
}
