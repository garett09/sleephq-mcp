package com.adriangarett.sleephqmcp.oscar;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class OscarChannelMapper {

    private static final Map<String, String> FIELD_NAMES = Map.ofEntries(
            Map.entry("RespRate",     "resp_rate"),
            Map.entry("TidalVolume",  "tidal_volume"),
            Map.entry("MinuteVent",   "minute_vent"),
            Map.entry("Ti",           "insp_time"),
            Map.entry("Te",           "exp_time"),
            Map.entry("FLG",          "flow_limit"),
            Map.entry("Leak",         "leak"),
            Map.entry("LeakTotal",    "leak_total"),
            Map.entry("LeakSpan",     "leak_span"),
            Map.entry("Pressure",     "pressure"),
            Map.entry("EPAP",         "epap"),
            Map.entry("EPAPHi",       "epap_max"),
            Map.entry("EPAPLo",       "epap_min"),
            Map.entry("IPAP",         "ipap"),
            Map.entry("IPAPHi",       "ipap_max"),
            Map.entry("IPAPLo",       "ipap_min"),
            Map.entry("Snore",        "snore"),
            Map.entry("FlowRate",     "flow_brp"),
            Map.entry("MaskPressure", "mask_pressure"),
            Map.entry("AHI",          "ahi"),
            Map.entry("SPO2",         "spo2"),
            Map.entry("PulseRate",    "pulse_rate"),
            Map.entry("NRI",          "nri"),
            Map.entry("VSnore",       "vsnore"),
            Map.entry("PLM",          "plm"),
            Map.entry("Arousal",      "arousal"),
            Map.entry("FlowAbnormality", "flow_abnormality")
    );

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("RespRate",     "BPM"),
            Map.entry("TidalVolume",  "ml"),
            Map.entry("MinuteVent",   "L/min"),
            Map.entry("Ti",           "s"),
            Map.entry("Te",           "s"),
            Map.entry("FLG",          ""),
            Map.entry("Leak",         "L/min"),
            Map.entry("LeakTotal",    "L/min"),
            Map.entry("LeakSpan",     "L/min"),
            Map.entry("Pressure",     "cmH2O"),
            Map.entry("EPAP",         "cmH2O"),
            Map.entry("EPAPHi",       "cmH2O"),
            Map.entry("EPAPLo",       "cmH2O"),
            Map.entry("IPAP",         "cmH2O"),
            Map.entry("IPAPHi",       "cmH2O"),
            Map.entry("IPAPLo",       "cmH2O"),
            Map.entry("Snore",        ""),
            Map.entry("FlowRate",     "L/min"),
            Map.entry("MaskPressure", "cmH2O"),
            Map.entry("AHI",          "/hr"),
            Map.entry("SPO2",         "%"),
            Map.entry("PulseRate",    "BPM")
    );

    private static final Set<String> EVENT_CHANNELS = Set.of(
            "Obstructive", "ClearAirway", "Hypopnea", "Apnea", "RERA",
            "NRI", "VSnore", "PLM", "Arousal", "AllApnea", "FlowLimit", "ExP", "CSR"
    );

    private static final Map<String, String> CANONICAL_EVENT_LABELS = Map.of(
            "Obstructive", "obstructive",
            "ClearAirway", "clear_airway",
            "Hypopnea",    "hypopnea",
            "Apnea",       "apnea",
            "RERA",        "rera",
            "NRI",         "nri",
            "VSnore",      "vsnore",
            "PLM",         "plm"
    );

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])");

    private OscarChannelMapper() {}

    public static String fieldName(String channelCode) {
        if (channelCode == null) return "unknown";
        String mapped = FIELD_NAMES.get(channelCode);
        if (mapped != null) return mapped;
        return CAMEL_BOUNDARY.matcher(channelCode).replaceAll("_").toLowerCase();
    }

    public static String unit(String channelCode) {
        return UNITS.getOrDefault(channelCode, "");
    }

    public static boolean isEventChannel(String channelCode) {
        return EVENT_CHANNELS.contains(channelCode);
    }

    public static String canonicalEventLabel(String channelCode) {
        return CANONICAL_EVENT_LABELS.get(channelCode);
    }
}
