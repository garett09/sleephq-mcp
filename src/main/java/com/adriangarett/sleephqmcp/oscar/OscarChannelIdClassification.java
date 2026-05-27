package com.adriangarett.sleephqmcp.oscar;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Classifies OSCAR channel IDs as event channels (discrete event counts) vs
 * continuous waveform/statistics channels.
 *
 * <p>Event channels (e.g. CPAP_ClearAirway) store per-session event counts.
 * Their "average" in a summary is not a meaningful continuous statistic and
 * must be excluded from the {@code channels.*} waveform stats block in the
 * night analysis output.
 */
public final class OscarChannelIdClassification {

    /** 13 channels that carry countable event totals in the {@code m_cnt} hash. */
    private static final Set<Integer> COUNTED_EVENT_CHANNEL_IDS = Set.of(
            OscarChannelIds.CPAP_ClearAirway,     // 0x1001
            OscarChannelIds.CPAP_Obstructive,     // 0x1002
            OscarChannelIds.CPAP_Hypopnea,        // 0x1003
            OscarChannelIds.CPAP_Apnea,           // 0x1004
            OscarChannelIds.CPAP_FlowLimit,       // 0x1005
            OscarChannelIds.CPAP_RERA,            // 0x1006
            OscarChannelIds.CPAP_VibratorySnore,  // 0x1007
            OscarChannelIds.CPAP_LargeLeak,       // 0x100A
            OscarChannelIds.CPAP_NRI,             // 0x100B
            OscarChannelIds.CPAP_ExpiratoryTime,  // 0x100C
            OscarChannelIds.CPAP_SensAwake,       // 0x100D
            OscarChannelIds.CPAP_AllApnea,        // 0x1010
            OscarChannelIds.CPAP_PressurePulse    // 0x1028
    );

    /** 14 channels = COUNTED_EVENT_CHANNEL_IDS + CPAP_UsageFlag (session flag, not a countable event). */
    private static final Set<Integer> EVENT_CHANNEL_IDS;
    static {
        Set<Integer> all = new LinkedHashSet<>(COUNTED_EVENT_CHANNEL_IDS);
        all.add(OscarChannelIds.CPAP_UsageFlag);
        EVENT_CHANNEL_IDS = Set.copyOf(all);
    }

    private OscarChannelIdClassification() {}

    /**
     * Returns {@code true} if the given channel ID represents a discrete event
     * channel (i.e. its summary values are event counts, not continuous stats).
     * Includes {@code CPAP_UsageFlag}.
     */
    public static boolean isEventChannel(int channelId) {
        return EVENT_CHANNEL_IDS.contains(channelId);
    }

    /**
     * Subset of {@link #isEventChannel} excluding session flags — channels that carry {@code m_cnt} event totals.
     */
    public static boolean isCountedEventChannel(int channelId) {
        return COUNTED_EVENT_CHANNEL_IDS.contains(channelId);
    }

    /** For callers that need the iterable form (e.g. {@code m_cnt} hash scanning). */
    public static Set<Integer> countedEventChannelIds() {
        return COUNTED_EVENT_CHANNEL_IDS;
    }
}
