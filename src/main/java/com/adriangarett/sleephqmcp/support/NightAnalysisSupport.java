package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.oscar.OscarChannelCatalog;
import com.adriangarett.sleephqmcp.oscar.OscarChannelIdClassification;
import com.adriangarett.sleephqmcp.oscar.OscarChannelIds;
import com.adriangarett.sleephqmcp.oscar.OscarSummaryHeaderParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NightAnalysisSupport {

    private NightAnalysisSupport() {}

    public static ObjectNode channelStatsNode(Map<String, ChannelStatistics> stats) {
        return channelStatsNode(stats, null);
    }

    public static ObjectNode channelStatsNode(Map<String, ChannelStatistics> stats, String oscarFreshness) {
        ObjectNode channels = JsonApi.mapper().createObjectNode();
        stats.forEach((key, stat) -> {
            ObjectNode ch = channels.putObject(key);
            ch.put("avg", stat.avg());
            ch.put("min", stat.min());
            ch.put("max", stat.max());
            ch.put("p95", stat.percentile());
            if (!Double.isNaN(stat.median())) {
                ch.put("median", stat.median());
            }
            if (stat.minAt() != null && !stat.minAt().isBlank()) {
                ch.put("min_at", stat.minAt());
            }
            if (stat.minAtSeconds() >= 0) {
                ch.put("min_at_seconds", stat.minAtSeconds());
            }
            if (stat.maxAt() != null && !stat.maxAt().isBlank()) {
                ch.put("max_at", stat.maxAt());
            }
            if (stat.maxAtSeconds() >= 0) {
                ch.put("max_at_seconds", stat.maxAtSeconds());
            }
            if (!stat.unit().isBlank()) {
                ch.put("unit", stat.unit());
            }
            if (stat.sampleCount() > 0) {
                ch.put("sample_count", stat.sampleCount());
            }
            if (oscarFreshness != null) {
                ch.put("freshness", oscarFreshness);
            }
        });
        return channels;
    }

    public static ObjectNode summaryChannelNode(OscarSession session) {
        return summaryChannelNode(session, null);
    }

    public static ObjectNode summaryChannelNode(OscarSession session, String oscarFreshness) {
        ObjectNode channels = JsonApi.mapper().createObjectNode();
        for (int channelId : session.availableChannelIds()) {
            if (OscarChannelIdClassification.isEventChannel(channelId)) {
                continue;
            }
            String field = OscarChannelCatalog.fieldName(channelId);
            ChannelSummary summary = session.channels().get(channelId);
            ObjectNode ch = channels.putObject(field);
            ch.put("channel_id", String.format("0x%04x", channelId));
            ch.put("label", OscarChannelCatalog.label(channelId));
            OscarChannelCatalog.find(channelId).ifPresent(meta -> ch.put("unit", meta.unit()));
            if (summary != null) {
                putIfPresent(ch, "avg", summary.avg());
                putIfPresent(ch, "min", summary.min());
                putIfPresent(ch, "max", summary.max());
                putIfPresent(ch, "wavg", summary.wavg());
            }
            ch.put("source", "oscar_summary");
            if (oscarFreshness != null) {
                ch.put("freshness", oscarFreshness);
            }
        }
        return channels;
    }

    public static ObjectNode respiratoryIndices(Optional<OscarSession> session, JsonNode machineDateAttrs) {
        ObjectNode indices = JsonApi.mapper().createObjectNode();
        session.ifPresent(s -> putOscarIndices(indices, s));
        putSleepHqIndices(indices, machineDateAttrs);
        coalesceLegacyIndices(indices);
        return indices;
    }

    public static ObjectNode sessionNode(
            OscarSessionIndexEntry session,
            OscarSummaryHeaderParser.SummaryHeader header) {
        ObjectNode node = JsonApi.mapper().createObjectNode();
        node.put("session_id", Long.toHexString(session.sessionId()));
        node.put("start", session.firstInstant().toString());
        node.put("end", session.lastInstant().toString());
        if (header != null) {
            node.put("duration_seconds", header.durationSeconds());
        }
        ArrayNode channelIds = node.putArray("channel_ids");
        session.channelIds().forEach(id -> channelIds.add(String.format("0x%04x", id)));
        return node;
    }

    public static ObjectNode coverageNode(
            boolean sleephqCpap,
            boolean summary,
            boolean pldPresent,
            boolean eve,
            boolean brp,
            int channelCount,
            boolean pldHasStats) {
        ObjectNode coverage = JsonApi.mapper().createObjectNode();
        coverage.put("sleephq_cpap", sleephqCpap);
        coverage.put("oscar_summary", summary);
        coverage.put("oscar_edf_pld", pldPresent);
        coverage.put("oscar_edf_pld_stats", pldHasStats);
        coverage.put("oscar_edf_eve", eve);
        coverage.put("oscar_edf_brp", brp);
        coverage.put("channels_reported", channelCount);
        return coverage;
    }

    public static ObjectNode coverageNode(
            boolean sleephqCpap,
            boolean summary,
            boolean pldPresent,
            boolean eve,
            boolean brp,
            int channelCount,
            boolean pldHasStats,
            Long oscarLagDays,
            String oscarFreshness) {
        ObjectNode coverage = coverageNode(sleephqCpap, summary, pldPresent, eve, brp, channelCount, pldHasStats);
        if (oscarLagDays != null) {
            coverage.put("oscar_lag_days", oscarLagDays);
        }
        if (oscarFreshness != null) {
            coverage.put("oscar_freshness", oscarFreshness);
        }
        return coverage;
    }

    public static void attachSleepHq(ObjectNode nightAnalysis, JsonNode machineDateAttrs, JsonNode journalAttrs) {
        if (machineDateAttrs == null && journalAttrs == null) {
            return;
        }
        ObjectNode sleephq = nightAnalysis.putObject("sleephq");
        if (machineDateAttrs != null && machineDateAttrs.isObject()) {
            if (machineDateAttrs.has("ahi_summary")) {
                sleephq.set("ahi_summary", machineDateAttrs.get("ahi_summary").deepCopy());
            }
            if (machineDateAttrs.has("spo2_summary")) {
                sleephq.set("spo2_summary", machineDateAttrs.get("spo2_summary").deepCopy());
            }
            if (machineDateAttrs.has("movement_summary")) {
                sleephq.set("movement_summary", machineDateAttrs.get("movement_summary").deepCopy());
            }
        }
        if (journalAttrs != null && journalAttrs.isObject() && journalAttrs.has("feeling_score")) {
            sleephq.set("journal_feeling", journalAttrs.get("feeling_score").deepCopy());
        }
    }

    public static void attachNotableMoments(ObjectNode nightAnalysis, List<ObjectNode> moments) {
        ArrayNode arr = nightAnalysis.putArray("notable_moments");
        moments.forEach(arr::add);
    }

    private static void putOscarIndices(ObjectNode indices, OscarSession session) {
        putIndexFromSummary(indices, "oscar_ahi_per_hr", session, OscarChannelIds.CPAP_AHI);
        putIndexFromSummary(indices, "oscar_rdi_per_hr", session, OscarChannelIds.CPAP_RDI);
    }

    private static void putSleepHqIndices(ObjectNode indices, JsonNode machineDateAttrs) {
        if (machineDateAttrs == null || !machineDateAttrs.isObject()) {
            return;
        }
        AhiSummarySupport.readComponents(machineDateAttrs.path("ahi_summary")).ifPresent(components -> {
            putRounded(indices, "sleephq_ahi_per_hr", components.ahiPerHr());
        });
    }

    private static void coalesceLegacyIndices(ObjectNode indices) {
        if (indices.has("sleephq_ahi_per_hr")) {
            indices.put("ahi_per_hr", indices.get("sleephq_ahi_per_hr").doubleValue());
        } else if (indices.has("oscar_ahi_per_hr")) {
            indices.put("ahi_per_hr", indices.get("oscar_ahi_per_hr").doubleValue());
        }
        if (indices.has("oscar_rdi_per_hr")) {
            indices.put("rdi_per_hr", indices.get("oscar_rdi_per_hr").doubleValue());
        }
    }

    private static void putIndexFromSummary(
            ObjectNode indices, String key, OscarSession session, int channelId) {
        ChannelSummary summary = session.channels().get(channelId);
        if (summary != null && summary.avg() != null) {
            putRounded(indices, key, summary.avg());
        }
    }

    private static void putRounded(ObjectNode indices, String key, double value) {
        indices.put(key, Math.round(value * 100.0) / 100.0);
    }

    private static void putIfPresent(ObjectNode node, String field, Double value) {
        if (value != null && !value.isNaN()) {
            node.put(field, Math.round(value * 100.0) / 100.0);
        }
    }
}
