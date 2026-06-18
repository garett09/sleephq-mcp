package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties;
import com.adriangarett.sleephqmcp.domain.WaveformWindowPlan;
import com.adriangarett.sleephqmcp.domain.WindowEvidence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class WaveformResponseSupport {

    private WaveformResponseSupport() {
    }

    public static String attachWindowSelection(
            String waveformJson, WaveformWindowPlan plan, SleepHqPayloadProperties payload) {
        try {
            ObjectNode root = JsonApi.parseObject(waveformJson);
            ObjectNode sel = root.putObject("window_selection");
            sel.put("anchor_requested", plan.anchorRequested());
            sel.put("anchor_resolved", plan.anchorResolved());
            sel.put("start_minute", plan.startMinute());
            sel.put("lead_in_minutes", plan.leadInMinutes());
            sel.put("max_minutes", plan.maxMinutes());
            sel.put("reason", plan.reason());
            if (plan.alignmentConfidence() != null && !plan.alignmentConfidence().isBlank()) {
                sel.put("alignment_confidence", plan.alignmentConfidence());
            }
            ArrayNode evidence = sel.putArray("evidence");
            for (WindowEvidence ev : plan.evidence()) {
                ObjectNode item = evidence.addObject();
                item.put("source", ev.source());
                item.put("label", ev.label());
                if (ev.startSeconds() != null) {
                    item.put("start_seconds", ev.startSeconds());
                }
                if (ev.timestamp() != null && !ev.timestamp().isBlank()) {
                    item.put("timestamp", ev.timestamp());
                }
            }
            if (payload != null) {
                McpPayloadHints.attach(root, payload);
            }
            return JsonApi.mapper().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to attach window_selection", e);
        }
    }
}
