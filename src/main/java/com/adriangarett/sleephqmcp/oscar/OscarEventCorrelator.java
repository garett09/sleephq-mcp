package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class OscarEventCorrelator {

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private OscarEventCorrelator() {}

    public static List<ObjectNode> buildNotableMoments(
            Map<String, ChannelStatistics> channels,
            List<DeviceEvent> events,
            String sessionStartIso,
            int correlationWindowSeconds,
            int maxMoments,
            int maxNearbyEvents) {
        LocalDateTime sessionStart = LocalDateTime.parse(sessionStartIso.substring(0, 19), ISO_LOCAL);
        List<MomentCandidate> candidates = new ArrayList<>();
        for (ChannelStatistics stats : channels.values()) {
            if (stats.maxAt() != null && !stats.maxAt().isBlank()) {
                candidates.add(new MomentCandidate(stats.fieldName(), "max", stats.max(), stats.maxAt()));
            }
            if (stats.minAt() != null && !stats.minAt().isBlank()) {
                candidates.add(new MomentCandidate(stats.fieldName(), "min", stats.min(), stats.minAt()));
            }
        }
        candidates.sort(Comparator.comparingDouble(MomentCandidate::value).reversed());
        List<ObjectNode> moments = new ArrayList<>();
        for (MomentCandidate candidate : candidates) {
            if (moments.size() >= maxMoments) {
                break;
            }
            LocalDateTime momentTime = sessionStart.plus(
                    parseClock(candidate.clock()), ChronoUnit.SECONDS);
            ObjectNode node = com.adriangarett.sleephqmcp.support.JsonApi.mapper().createObjectNode();
            node.put("channel", candidate.channel());
            node.put("kind", candidate.kind());
            node.put("value", candidate.value());
            node.put("clock", candidate.clock());
            node.put("timestamp", momentTime.format(ISO_LOCAL));
            ArrayNode nearby = node.putArray("nearby_events");
            int added = 0;
            for (DeviceEvent event : events) {
                if (added >= maxNearbyEvents) {
                    break;
                }
                LocalDateTime eventTime = LocalDateTime.parse(event.timestamp().substring(0, 19), ISO_LOCAL);
                long delta = Math.abs(ChronoUnit.SECONDS.between(momentTime, eventTime));
                if (delta <= correlationWindowSeconds) {
                    ObjectNode ev = nearby.addObject();
                    ev.put("timestamp", event.timestamp());
                    ev.put("label", event.label());
                    ev.put("delta_seconds", delta);
                    added++;
                }
            }
            if (!nearby.isEmpty()) {
                moments.add(node);
            }
        }
        return moments;
    }

    private static long parseClock(String clock) {
        String[] parts = clock.split(":");
        if (parts.length != 3) {
            return 0;
        }
        return Long.parseLong(parts[0]) * 3600L
                + Long.parseLong(parts[1]) * 60L
                + Long.parseLong(parts[2]);
    }

    private record MomentCandidate(String channel, String kind, double value, String clock) {}
}
