package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.oscar.OscarChannelMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NightAnalysisSupport {

    private NightAnalysisSupport() {}

    public static ObjectNode channelStatsNode(Map<String, ChannelStatistics> stats) {
        ObjectNode channels = JsonApi.mapper().createObjectNode();
        stats.forEach((key, stat) -> {
            ObjectNode ch = channels.putObject(key);
            if (!Double.isNaN(stat.avg())) ch.put("avg", stat.avg());
            if (!Double.isNaN(stat.min())) ch.put("min", stat.min());
            if (!Double.isNaN(stat.max())) ch.put("max", stat.max());
            if (!Double.isNaN(stat.percentile())) ch.put("p95", stat.percentile());
            if (!Double.isNaN(stat.p995())) {
                ch.put("p99_5", stat.p995());
            }
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
        });
        return channels;
    }

    public static ObjectNode summaryChannelNode(OscarSession session) {
        ObjectNode channels = JsonApi.mapper().createObjectNode();
        for (String code : session.availableChannelCodes()) {
            if (OscarChannelMapper.isEventChannel(code)) {
                continue;
            }
            String field = OscarChannelMapper.fieldName(code);
            ChannelSummary summary = session.channels().get(code);
            ObjectNode ch = channels.putObject(field);
            ch.put("channel_code", code);
            String unit = OscarChannelMapper.unit(code);
            if (!unit.isBlank()) {
                ch.put("unit", unit);
            }
            if (summary != null) {
                putIfPresent(ch, "avg", summary.avg());
                putIfPresent(ch, "min", summary.min());
                putIfPresent(ch, "max", summary.max());
                putIfPresent(ch, "wavg", summary.wavg());
            }
            ch.put("source", "oscar_summary");
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
            Map<String, Double> header) {
        ObjectNode node = JsonApi.mapper().createObjectNode();
        node.put("session_id", Long.toHexString(session.sessionId()));
        node.put("start", session.firstInstant().toString());
        node.put("end", session.lastInstant().toString());
        ArrayNode channelCodes = node.putArray("channel_codes");
        session.channelCodes().forEach(channelCodes::add);
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
            Long oscarExportLagDays,
            String oscarExportFreshness) {
        ObjectNode coverage = coverageNode(sleephqCpap, summary, pldPresent, eve, brp, channelCount, pldHasStats);
        if (oscarExportLagDays != null) {
            coverage.put("oscar_export_lag_days", oscarExportLagDays);
        }
        if (oscarExportFreshness != null) {
            coverage.put("oscar_export_freshness", oscarExportFreshness);
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
        putIndexFromSummary(indices, "oscar_ahi_per_hr", session);
        putIndexFromSummary(indices, "oscar_rdi_per_hr", session);
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

    private static void putIndexFromSummary(ObjectNode indices, String key, OscarSession session) {
        // key = "oscar_ahi_per_hr" → target field = "ahi"; "oscar_rdi_per_hr" → "rdi"
        String targetField = key.replace("oscar_", "").replace("_per_hr", "");
        session.channels().entrySet().stream()
                .filter(e -> OscarChannelMapper.fieldName(e.getKey()).equals(targetField))
                .map(Map.Entry::getValue)
                .filter(s -> s.avg() != null)
                .findFirst()
                .ifPresent(s -> putRounded(indices, key, s.avg()));
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
