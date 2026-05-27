package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.oscar.OscarChannelIds;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// static import for channel constants used in new tests
import static com.adriangarett.sleephqmcp.oscar.OscarChannelIds.CPAP_ClearAirway;
import static com.adriangarett.sleephqmcp.oscar.OscarChannelIds.CPAP_Obstructive;
import static com.adriangarett.sleephqmcp.oscar.OscarChannelIds.CPAP_Pressure;

import static org.assertj.core.api.Assertions.assertThat;

class NightAnalysisSupportTest {

    @Test
    void respiratoryIndices_emitsOscarAndSleepHqScalarsWithoutPlaceholders() {
        OscarSession session = new OscarSession(
                "2026-05-18",
                0x6A09F5B4L,
                0L,
                28_800L,
                Map.of(OscarChannelIds.CPAP_AHI, new ChannelSummary(1.24, null, null, null, null, null)),
                List.of(OscarChannelIds.CPAP_AHI));
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.putObject("ahi_summary").put("av", 1.20).put("oa", 0.5).put("ca", 0.1);

        ObjectNode indices = NightAnalysisSupport.respiratoryIndices(Optional.of(session), attrs);

        assertThat(indices.get("oscar_ahi_per_hr").asDouble()).isEqualTo(1.24);
        assertThat(indices.get("sleephq_ahi_per_hr").asDouble()).isEqualTo(1.20);
        assertThat(indices.get("ahi_per_hr").asDouble()).isEqualTo(1.20);
        assertThat(indices.has("see_channels.ahi")).isFalse();
    }

    @Test
    void respiratoryIndices_omitsAhiWhenNoSources() {
        ObjectNode indices = NightAnalysisSupport.respiratoryIndices(Optional.empty(), null);
        assertThat(indices.has("ahi_per_hr")).isFalse();
        assertThat(indices.has("oscar_ahi_per_hr")).isFalse();
        assertThat(indices.has("sleephq_ahi_per_hr")).isFalse();
    }

    @Test
    void respiratoryIndices_oscarOnlyPopulatesCoalescedAhi() {
        OscarSession session = new OscarSession(
                "2026-05-18",
                0x6A09F5B4L,
                0L,
                28_800L,
                Map.of(OscarChannelIds.CPAP_AHI, new ChannelSummary(2.0, null, null, null, null, null)),
                List.of(OscarChannelIds.CPAP_AHI));

        ObjectNode indices = NightAnalysisSupport.respiratoryIndices(Optional.of(session), null);

        assertThat(indices.get("oscar_ahi_per_hr").asDouble()).isEqualTo(2.0);
        assertThat(indices.get("ahi_per_hr").asDouble()).isEqualTo(2.0);
        assertThat(indices.has("sleephq_ahi_per_hr")).isFalse();
    }

    @Test
    void coverageDistinguishesPldPresenceFromPldStats() {
        // pldPresent=true, pldHasStats=true → both flags true
        ObjectNode withStats = NightAnalysisSupport.coverageNode(false, false, true, false, false, 0, true);
        assertThat(withStats.get("oscar_edf_pld").asBoolean()).isTrue();
        assertThat(withStats.get("oscar_edf_pld_stats").asBoolean()).isTrue();

        // pldPresent=true, pldHasStats=false → pld true, pld_stats false
        ObjectNode noStats = NightAnalysisSupport.coverageNode(false, false, true, false, false, 0, false);
        assertThat(noStats.get("oscar_edf_pld").asBoolean()).isTrue();
        assertThat(noStats.get("oscar_edf_pld_stats").asBoolean()).isFalse();
    }

    @Test
    void summaryChannelNode_includesWaveformChannelButNotEventChannels() {
        OscarSession session = new OscarSession(
                "2026-05-27",
                0x1234L,
                0L,
                28_800L,
                Map.of(
                        CPAP_Pressure,    new ChannelSummary(10.0, 8.0, 14.0, null, null, null),
                        CPAP_ClearAirway, new ChannelSummary(13.0, 0.0, 20.0, null, null, null),
                        CPAP_Obstructive, new ChannelSummary(5.0,  0.0, 10.0, null, null, null)
                ),
                List.of(CPAP_Pressure, CPAP_ClearAirway, CPAP_Obstructive));

        ObjectNode node = NightAnalysisSupport.summaryChannelNode(session);

        assertThat(node.has("pressure")).isTrue();
        assertThat(node.has("clear_airway")).isFalse();
        assertThat(node.has("obstructive")).isFalse();
    }
}
