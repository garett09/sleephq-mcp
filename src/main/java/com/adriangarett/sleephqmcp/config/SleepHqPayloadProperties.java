package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP tool payload limits for LLM clients. Defaults target large-context sessions (~500k–1M tokens).
 * Override via {@code sleephq.mcp.payload.*} in env or {@code application.properties}.
 */
@ConfigurationProperties(prefix = "sleephq.mcp.payload")
public record SleepHqPayloadProperties(
        int waveformDefaultMaxMinutes,
        int waveformMaxMinutesCap,
        int waveformMaxSamplesPerChannel,
        int o2RecommendedMaxMinutes,
        String profileNote) {

    public static final int LEGACY_WAVEFORM_DEFAULT_MAX_MINUTES = 3;
    public static final int LEGACY_WAVEFORM_MAX_MINUTES_CAP = 30;
    public static final int LEGACY_WAVEFORM_MAX_SAMPLES_PER_CHANNEL = 500;

    public SleepHqPayloadProperties {
        if (waveformDefaultMaxMinutes < 1) {
            waveformDefaultMaxMinutes = 10;
        }
        if (waveformMaxMinutesCap < waveformDefaultMaxMinutes) {
            waveformMaxMinutesCap = Math.max(waveformDefaultMaxMinutes, 60);
        }
        if (waveformMaxSamplesPerChannel < 100) {
            waveformMaxSamplesPerChannel = 4000;
        }
        if (o2RecommendedMaxMinutes < 1) {
            o2RecommendedMaxMinutes = 45;
        }
        if (profileNote == null || profileNote.isBlank()) {
            profileNote = "Large-context defaults: richer waveform/O2 windows; scan-apnea-events stays full-night.";
        }
    }
}
