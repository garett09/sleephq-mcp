package com.adriangarett.sleephqmcp.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Each channel maps to a SleepHQ {@code /api/v1/machine_dates/{id}/{pathSegment}} endpoint.
 * {@code nativeSampleRateHz} is the device's recording rate (ResMed CPAP flow rate is 25 Hz;
 * O2 Ring SpO2 and pulse-rate are 1 Hz) and is reported back to the LLM so it knows the
 * temporal resolution of the data it received.
 */
public enum WaveformChannel {

    FLOW_RATE("flow_rate_data", 25.0, "L/min", "Flow rate"),
    PRESSURE("pressure_data", 25.0, "cmH2O", "Pressure"),
    LEAK("leak_data", 25.0, "L/min", "Leak rate"),
    /** CPAP-derived trace; absent on some nights until SleepHQ finishes import — see docs/sleephq-waveform-segments.md */
    TIDAL_VOLUME("tidal_volume_data", 25.0, "mL", "Tidal volume"),
    SPO2("spo2_data", 1.0, "%", "SpO2"),
    PULSE_RATE("pulse_rate_data", 1.0, "bpm", "Pulse rate");

    private final String pathSegment;
    private final double nativeSampleRateHz;
    private final String unit;
    private final String label;

    WaveformChannel(String pathSegment, double nativeSampleRateHz, String unit, String label) {
        this.pathSegment = pathSegment;
        this.nativeSampleRateHz = nativeSampleRateHz;
        this.unit = unit;
        this.label = label;
    }

    public String pathSegment() {
        return pathSegment;
    }

    public double nativeSampleRateHz() {
        return nativeSampleRateHz;
    }

    public String unit() {
        return unit;
    }

    public String label() {
        return label;
    }

    /**
     * Ensures {@code candidate} matches a known waveform endpoint segment (defense in depth for HTTP client calls).
     */
    public static String requireKnownPathSegment(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("channel path segment is required");
        }
        for (WaveformChannel channel : values()) {
            if (channel.pathSegment.equals(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown waveform channel path segment");
    }

    /**
     * Parses a comma-separated list of channel tokens (enum names e.g. {@code flow_rate},
     * or API path segments e.g. {@code flow_rate_data}). Order is preserved; duplicates are kept.
     */
    public static List<WaveformChannel> parseChannelList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            throw new IllegalArgumentException("channels is required (comma-separated list, e.g. flow_rate,pressure,leak)");
        }
        List<WaveformChannel> out = new ArrayList<>();
        for (String part : commaSeparated.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            WaveformChannel resolved = null;
            for (WaveformChannel channel : values()) {
                if (channel.name().equalsIgnoreCase(token) || channel.pathSegment.equalsIgnoreCase(token)) {
                    resolved = channel;
                    break;
                }
            }
            if (resolved == null) {
                throw new IllegalArgumentException("Unknown waveform channel token: " + token);
            }
            out.add(resolved);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("channels contained no valid tokens");
        }
        if (out.size() > 6) {
            throw new IllegalArgumentException("At most 6 channels per correlation request");
        }
        return out;
    }
}
