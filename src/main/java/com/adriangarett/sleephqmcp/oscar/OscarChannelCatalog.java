package com.adriangarett.sleephqmcp.oscar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class OscarChannelCatalog {

    private static final Map<Integer, ChannelMeta> BY_ID = buildIndex();

    private OscarChannelCatalog() {}

    public static Optional<ChannelMeta> find(int channelId) {
        return Optional.ofNullable(BY_ID.get(channelId));
    }

    public static String fieldName(int channelId) {
        return find(channelId).map(ChannelMeta::fieldName).orElse("ch_" + Integer.toHexString(channelId));
    }

    public static String label(int channelId) {
        return find(channelId).map(ChannelMeta::label).orElse("Channel " + Integer.toHexString(channelId));
    }

    public static Optional<ChannelMeta> findByFieldName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return Optional.empty();
        }
        return BY_ID.values().stream()
                .filter(meta -> meta.fieldName().equals(fieldName))
                .findFirst();
    }

    private static Map<Integer, ChannelMeta> buildIndex() {
        Map<Integer, ChannelMeta> m = new LinkedHashMap<>();
        m.put(OscarChannelIds.CPAP_UsageFlag, meta("usage_flag", "Usage flag", ""));
        m.put(OscarChannelIds.CPAP_FlowRate, meta("flow_rate", "Flow", "L/min"));
        m.put(OscarChannelIds.CPAP_MaskPressure, meta("mask_pressure", "Mask pressure", "cmH2O"));
        m.put(OscarChannelIds.CPAP_FlowRateHiRes, meta("flow_rate_hi_res", "Flow rate (hi-res)", "L/min"));
        m.put(OscarChannelIds.CPAP_TidalVolume, meta("tidal_volume", "Tidal volume", "mL"));
        m.put(OscarChannelIds.CPAP_Snore, meta("snore", "Snore", ""));
        m.put(OscarChannelIds.CPAP_MinuteVent, meta("minute_vent", "Minute vent", "L/min"));
        m.put(OscarChannelIds.CPAP_RespRate, meta("resp_rate", "Resp rate", "bpm"));
        m.put(OscarChannelIds.CPAP_Leak, meta("leak", "Leak", "L/min"));
        m.put(OscarChannelIds.CPAP_Pressure, meta("pressure", "Pressure", "cmH2O"));
        m.put(OscarChannelIds.CPAP_IPAP, meta("ipap", "IPAP", "cmH2O"));
        m.put(OscarChannelIds.CPAP_EPAP, meta("epap", "EPAP", "cmH2O"));
        m.put(OscarChannelIds.CPAP_AHI, meta("ahi", "AHI", "/h"));
        m.put(OscarChannelIds.CPAP_RDI, meta("rdi", "RDI", "/h"));
        m.put(OscarChannelIds.CPAP_SessionMetric, meta("session_metric", "Session metric", ""));
        m.put(OscarChannelIds.CPAP_LeakTotal, meta("leak_total", "Leak total", "L/min"));
        m.put(OscarChannelIds.CPAP_LeakMedian, meta("leak_median", "Leak median", "L/min"));
        m.put(OscarChannelIds.CPAP_MaxLeak, meta("leak_max", "Leak max", "L/min"));
        m.put(OscarChannelIds.CPAP_FlowLimitGraph, meta("flow_limit", "Flow limitation", ""));
        m.put(OscarChannelIds.CPAP_ClearAirway, meta("clear_airway", "Clear airway", "events"));
        m.put(OscarChannelIds.CPAP_Obstructive, meta("obstructive", "Obstructive", "events"));
        m.put(OscarChannelIds.CPAP_Hypopnea, meta("hypopnea", "Hypopnea", "events"));
        m.put(OscarChannelIds.CPAP_Apnea, meta("apnea", "Apnea", "events"));
        m.put(OscarChannelIds.CPAP_FlowLimit, meta("flow_limit_events", "Flow limit", "events"));
        m.put(OscarChannelIds.CPAP_RERA, meta("rera", "RERA", "events"));
        m.put(OscarChannelIds.CPAP_VibratorySnore, meta("vibratory_snore", "Vibratory snore", "events"));
        m.put(OscarChannelIds.CPAP_LargeLeak, meta("large_leak", "Large leak", "events"));
        m.put(OscarChannelIds.CPAP_NRI, meta("nri", "NRI", "events"));
        m.put(OscarChannelIds.CPAP_ExpiratoryTime, meta("expiratory_time", "Expiratory time", "events"));
        m.put(OscarChannelIds.CPAP_SensAwake, meta("sens_awake", "Sens awake", "events"));
        m.put(OscarChannelIds.CPAP_AllApnea, meta("all_apnea", "All apnea", "events"));
        m.put(OscarChannelIds.CPAP_PressurePulse, meta("pressure_pulse", "Pressure pulse", "events"));
        m.put(OscarChannelIds.OXI_Pulse, meta("pulse", "Pulse", "bpm"));
        m.put(OscarChannelIds.OXI_SPO2, meta("spo2", "SpO2", "%"));
        return Map.copyOf(m);
    }

    private static ChannelMeta meta(String fieldName, String label, String unit) {
        return new ChannelMeta(fieldName, label, unit);
    }

    public record ChannelMeta(String fieldName, String label, String unit) {}
}
