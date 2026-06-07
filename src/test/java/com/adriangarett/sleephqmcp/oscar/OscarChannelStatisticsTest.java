package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OscarChannelStatisticsTest {

    // 0x110C = CPAP_Pressure (waveform)
    private static final int PRESSURE_ID    = OscarChannelIds.CPAP_Pressure;     // 0x110C
    // 0x1001 = CPAP_ClearAirway (event)
    private static final int CLEAR_AIRWAY   = OscarChannelIds.CPAP_ClearAirway;  // 0x1001
    // 0x1002 = CPAP_Obstructive (event)
    private static final int OBSTRUCTIVE    = OscarChannelIds.CPAP_Obstructive;  // 0x1002

    private OscarSession mixedSession() {
        // TODO(Task 8): updated to use String channel codes in OSCAR 2.0
        Map<String, ChannelSummary> channels = Map.of(
                "Pressure",    new ChannelSummary(10.0, 8.0, 14.0, null, null, null),
                "ClearAirway", new ChannelSummary(13.0, 0.0, 20.0, null, null, null),
                "Obstructive", new ChannelSummary(5.0,  0.0, 10.0, null, null, null)
        );
        return new OscarSession(
                "2026-05-27",
                0x1234L,
                0L,
                28_800L,
                channels,
                Map.of());
    }

    @Test
    void fromSummarySession_includesWaveformChannel() {
        Map<String, ChannelStatistics> stats = OscarChannelStatistics.fromSummarySession(mixedSession());
        assertThat(stats).containsKey("pressure");
    }

    @Test
    void fromSummarySession_excludesClearAirwayEventChannel() {
        Map<String, ChannelStatistics> stats = OscarChannelStatistics.fromSummarySession(mixedSession());
        assertThat(stats).doesNotContainKey("clear_airway");
    }

    @Test
    void fromSummarySession_excludesObstructiveEventChannel() {
        Map<String, ChannelStatistics> stats = OscarChannelStatistics.fromSummarySession(mixedSession());
        assertThat(stats).doesNotContainKey("obstructive");
    }

    @Test
    void fromSummarySession_percentileIsNaNWhenUnknown() {
        Map<String, ChannelStatistics> stats = OscarChannelStatistics.fromSummarySession(mixedSession());
        ChannelStatistics pressure = stats.get("pressure");
        assertThat(pressure).isNotNull();
        assertThat(pressure.percentile()).isNaN();
        assertThat(pressure.max()).isEqualTo(14.0);
    }

    @Test
    void fromSummarySession_p995IsNaNWhenUnknown() {
        Map<String, ChannelStatistics> stats = OscarChannelStatistics.fromSummarySession(mixedSession());
        ChannelStatistics pressure = stats.get("pressure");
        assertThat(pressure).isNotNull();
        assertThat(pressure.p995()).isNaN();
    }
}
