package com.adriangarett.sleephqmcp.domain;

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
}
