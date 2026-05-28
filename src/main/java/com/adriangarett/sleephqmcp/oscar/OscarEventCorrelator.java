package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
            if (stats.maxAtSeconds() >= 0) {
                candidates.add(new MomentCandidate(stats.fieldName(), "max", stats.max(),
                        stats.maxAtSeconds(), stats.maxAt(), stats.max() - stats.avg()));
            }
            if (stats.minAtSeconds() >= 0) {
                candidates.add(new MomentCandidate(stats.fieldName(), "min", stats.min(),
                        stats.minAtSeconds(), stats.minAt(), stats.avg() - stats.min()));
            }
        }
        candidates.sort(Comparator.comparingDouble(MomentCandidate::deviation).reversed());
        List<ObjectNode> moments = new ArrayList<>();
        for (MomentCandidate candidate : candidates) {
            if (moments.size() >= maxMoments) {
                break;
            }
            LocalDateTime momentTime = sessionStart.plusSeconds(candidate.offsetSeconds());
            ObjectNode node = com.adriangarett.sleephqmcp.support.JsonApi.mapper().createObjectNode();
            node.put("channel", candidate.channel());
            node.put("kind", candidate.kind());
            node.put("value", candidate.value());
            node.put("offset_seconds", candidate.offsetSeconds());
            node.put("clock", candidate.clock());
            node.put("timestamp", momentTime.format(ISO_LOCAL));
            List<long[]> inWindow = new ArrayList<>();
            for (int i = 0; i < events.size(); i++) {
                long delta = Math.abs(Math.round(candidate.offsetSeconds() - events.get(i).startSeconds()));
                if (delta <= correlationWindowSeconds) {
                    inWindow.add(new long[]{delta, i});
                }
            }
            inWindow.sort(Comparator.comparingLong(e -> e[0]));
            ArrayNode nearby = node.putArray("nearby_events");
            for (int i = 0; i < Math.min(maxNearbyEvents, inWindow.size()); i++) {
                DeviceEvent event = events.get((int) inWindow.get(i)[1]);
                ObjectNode ev = nearby.addObject();
                ev.put("timestamp", event.timestamp());
                ev.put("label", event.label());
                ev.put("delta_seconds", inWindow.get(i)[0]);
            }
            if (!nearby.isEmpty()) {
                moments.add(node);
            }
        }
        return moments;
    }

    private record MomentCandidate(String channel, String kind, double value, long offsetSeconds, String clock, double deviation) {}
}
