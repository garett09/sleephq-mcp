package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Surfaces configured payload limits so Goose/agents align tool args with server caps.
 */
public final class McpPayloadHints {

    private McpPayloadHints() {
    }

    public static void attach(ObjectNode target, SleepHqPayloadProperties payload) {
        ObjectNode hints = target.putObject("mcp_payload_hints");
        hints.put("waveform_default_max_minutes", payload.waveformDefaultMaxMinutes());
        hints.put("waveform_max_minutes_cap", payload.waveformMaxMinutesCap());
        hints.put("waveform_max_samples_per_channel", payload.waveformMaxSamplesPerChannel());
        hints.put("o2_recommended_max_minutes", payload.o2RecommendedMaxMinutes());
        hints.put("scan_apnea_events", "full_night (no minute cap)");
        hints.put("get_device_events", "full_night (no minute cap)");
        hints.put("note", payload.profileNote());
        hints.put("goose_when_context_1m",
                "Omit maxMinutes on get-waveform-by-date to use server default ("
                        + payload.waveformDefaultMaxMinutes() + " min, cap "
                        + payload.waveformMaxMinutesCap() + "). Pass a smaller maxMinutes only for a tight slice. "
                        + "O2 maxMinutes=" + payload.o2RecommendedMaxMinutes() + " on desat/leak nights; "
                        + "include scan event lists in appendix, not counts only.");
    }
}
