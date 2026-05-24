package com.adriangarett.sleephqmcp.domain;

/**
 * A single waveform datum. {@code t} is seconds from start of the recording.
 */
public record WaveformSample(double t, double v) {
}
