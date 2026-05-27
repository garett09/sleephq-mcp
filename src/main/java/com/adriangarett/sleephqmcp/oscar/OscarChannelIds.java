package com.adriangarett.sleephqmcp.oscar;

/**
 * OSCAR {@code ChannelID} values from {@code schema.cpp} (ResMed CPAP).
 */
public final class OscarChannelIds {

    // Event flags
    /** ResMed session flag channel (present in Summaries.xml.gz channel lists). */
    public static final int CPAP_UsageFlag = 0x1000;
    public static final int CPAP_ClearAirway = 0x1001;
    public static final int CPAP_Obstructive = 0x1002;
    public static final int CPAP_Hypopnea = 0x1003;
    public static final int CPAP_Apnea = 0x1004;
    public static final int CPAP_FlowLimit = 0x1005;
    public static final int CPAP_RERA = 0x1006;
    public static final int CPAP_VibratorySnore = 0x1007;
    public static final int CPAP_LargeLeak = 0x100A;
    public static final int CPAP_NRI = 0x100B;
    public static final int CPAP_ExpiratoryTime = 0x100C;
    public static final int CPAP_SensAwake = 0x100D;
    public static final int CPAP_AllApnea = 0x1010;
    public static final int CPAP_PressurePulse = 0x1028;

    // Waveforms
    public static final int CPAP_FlowRate = 0x1100;
    public static final int CPAP_MaskPressure = 0x1101;
    /** ResMed backup waveform slot between mask pressure and tidal volume. */
    public static final int CPAP_FlowRateHiRes = 0x1102;
    public static final int CPAP_TidalVolume = 0x1103;
    public static final int CPAP_Snore = 0x1104;
    public static final int CPAP_MinuteVent = 0x1105;
    public static final int CPAP_RespRate = 0x1106;
    public static final int CPAP_PTB = 0x1107;
    public static final int CPAP_Leak = 0x1108;
    public static final int CPAP_IE = 0x1109;
    public static final int CPAP_ExpiratoryTimeWave = 0x110A;
    public static final int CPAP_InspiratoryTime = 0x110B;
    public static final int CPAP_Pressure = 0x110C;
    public static final int CPAP_IPAP = 0x110D;
    public static final int CPAP_EPAP = 0x110E;
    public static final int CPAP_PressureSupport = 0x110F;
    public static final int CPAP_FlowLimitGraph = 0x1113;
    public static final int CPAP_TgtMinVent = 0x1114;
    public static final int CPAP_MaxLeak = 0x1115;
    public static final int CPAP_AHI = 0x1116;
    public static final int CPAP_LeakTotal = 0x1117;
    public static final int CPAP_LeakMedian = 0x1118;
    public static final int CPAP_RDI = 0x1119;
    /**
     * ResMed extended summary metric introduced on recent AS11 backups (Summaries.xml.gz).
     * Exact semantic is not in this repo's schema dump — observed values vary widely night-to-night
     * (e.g. 62.54 on one night, 0 on another). Treat as opaque; do not derive clinical conclusions.
     */
    public static final int CPAP_SessionMetric = 0x1158;

    // Settings (examples)
    public static final int CPAP_Mode = 0x1200;
    public static final int CPAP_PressureMin = 0x1020;
    public static final int CPAP_PressureMax = 0x1021;

    // Oximetry
    public static final int OXI_Pulse = 0x1800;
    public static final int OXI_SPO2 = 0x1801;

    private OscarChannelIds() {}
}
