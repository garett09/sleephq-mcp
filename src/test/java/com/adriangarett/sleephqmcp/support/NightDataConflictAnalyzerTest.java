package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.oscar.OscarChannelIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NightDataConflictAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void analyze_withEmptyInputs_returnsEmptyArray() {
        ArrayNode conflicts = NightDataConflictAnalyzer.analyze(null, null);
        assertThat(conflicts).isEmpty();
    }

    @Test
    void analyze_noDiscrepancies_returnsEmptyArray() {
        ObjectNode shqAttrs = mapper.createObjectNode();
        ObjectNode shqAhiSummary = shqAttrs.putObject("ahi_summary");
        shqAhiSummary.put("av", 1.2);
        shqAhiSummary.put("oa", 0.4);
        shqAhiSummary.put("ca", 0.3);
        shqAhiSummary.put("h", 0.5);

        ObjectNode shqPresSummary = shqAttrs.putObject("pressure_summary");
        shqPresSummary.put("avg", 9.5);

        ObjectNode shqLeakSummary = shqAttrs.putObject("leak_rate_summary");
        shqLeakSummary.put("avg", 2.1);

        Map<Integer, ChannelSummary> oscarChannels = new HashMap<>();
        oscarChannels.put(OscarChannelIds.CPAP_AHI, new ChannelSummary(1.2, 0.0, 5.0, 1.2, null, null));
        oscarChannels.put(OscarChannelIds.CPAP_Pressure, new ChannelSummary(9.5, 4.0, 12.0, 9.5, null, null));
        oscarChannels.put(OscarChannelIds.CPAP_Leak, new ChannelSummary(2.1, 0.0, 10.0, 2.1, null, null));

        // counts: oa count = 4, ca count = 3, hypopnea count = 5
        oscarChannels.put(OscarChannelIds.CPAP_Obstructive, new ChannelSummary(null, null, null, null, 4.0, null));
        oscarChannels.put(OscarChannelIds.CPAP_ClearAirway, new ChannelSummary(null, null, null, null, 3.0, null));
        oscarChannels.put(OscarChannelIds.CPAP_Hypopnea, new ChannelSummary(null, null, null, null, 5.0, null));

        OscarSession session = new OscarSession(
                LocalDate.now().toString(),
                12345678L,
                System.currentTimeMillis(),
                36000,
                oscarChannels,
                List.of(OscarChannelIds.CPAP_AHI, OscarChannelIds.CPAP_Pressure, OscarChannelIds.CPAP_Leak,
                        OscarChannelIds.CPAP_Obstructive, OscarChannelIds.CPAP_ClearAirway, OscarChannelIds.CPAP_Hypopnea)
        );

        ArrayNode conflicts = NightDataConflictAnalyzer.analyze(shqAttrs, session);
        assertThat(conflicts).isEmpty();
    }

    @Test
    void analyze_withDiscrepancies_populatesConflictsWithCorrectSeverities() {
        ObjectNode shqAttrs = mapper.createObjectNode();
        ObjectNode shqAhiSummary = shqAttrs.putObject("ahi_summary");
        shqAhiSummary.put("av", 1.2);  // oscar: 3.5 -> diff: 2.3 (critical)
        shqAhiSummary.put("oa", 0.4);  // oscar: 1.0 -> diff: 0.6 (warn)
        shqAhiSummary.put("ca", 0.3);  // oscar: 0.5 -> diff: 0.2 (info)
        shqAhiSummary.put("h", 0.5);   // oscar: 2.0 -> diff: 1.5 (critical)

        ObjectNode shqPresSummary = shqAttrs.putObject("pressure_summary");
        shqPresSummary.put("avg", 9.5); // oscar: 11.0 -> diff: 1.5 (warn)

        ObjectNode shqLeakSummary = shqAttrs.putObject("leak_rate_summary");
        shqLeakSummary.put("avg", 2.1); // oscar: 8.5 -> diff: 6.4 (warn)

        Map<Integer, ChannelSummary> oscarChannels = new HashMap<>();
        oscarChannels.put(OscarChannelIds.CPAP_AHI, new ChannelSummary(3.5, 0.0, 10.0, 3.5, null, null));
        oscarChannels.put(OscarChannelIds.CPAP_Pressure, new ChannelSummary(11.0, 4.0, 15.0, 11.0, null, null));
        oscarChannels.put(OscarChannelIds.CPAP_Leak, new ChannelSummary(8.5, 0.0, 20.0, 8.5, null, null));

        // Duration of 10 hours (36000 seconds)
        oscarChannels.put(OscarChannelIds.CPAP_Obstructive, new ChannelSummary(null, null, null, null, 10.0, null));
        oscarChannels.put(OscarChannelIds.CPAP_ClearAirway, new ChannelSummary(null, null, null, null, 5.0, null));
        oscarChannels.put(OscarChannelIds.CPAP_Hypopnea, new ChannelSummary(null, null, null, null, 20.0, null));

        OscarSession session = new OscarSession(
                LocalDate.now().toString(),
                12345678L,
                System.currentTimeMillis(),
                36000,
                oscarChannels,
                List.of(OscarChannelIds.CPAP_AHI, OscarChannelIds.CPAP_Pressure, OscarChannelIds.CPAP_Leak,
                        OscarChannelIds.CPAP_Obstructive, OscarChannelIds.CPAP_ClearAirway, OscarChannelIds.CPAP_Hypopnea)
        );

        ArrayNode conflicts = NightDataConflictAnalyzer.analyze(shqAttrs, session);

        assertThat(conflicts).hasSize(6);

        // Verify AHI Conflict
        ObjectNode ahiConflict = findConflictByMetric(conflicts, "ahi");
        assertThat(ahiConflict).isNotNull();
        assertThat(ahiConflict.get("sleephq_value").asDouble()).isEqualTo(1.2);
        assertThat(ahiConflict.get("oscar_value").asDouble()).isEqualTo(3.5);
        assertThat(ahiConflict.get("delta").asDouble()).isEqualTo(2.3);
        assertThat(ahiConflict.get("severity").asText()).isEqualTo("critical");

        // Verify Obstructive Apnea Index Conflict
        ObjectNode oaConflict = findConflictByMetric(conflicts, "obstructive_apnea_index");
        assertThat(oaConflict).isNotNull();
        assertThat(oaConflict.get("sleephq_value").asDouble()).isEqualTo(0.4);
        assertThat(oaConflict.get("oscar_value").asDouble()).isEqualTo(1.0);
        assertThat(oaConflict.get("delta").asDouble()).isEqualTo(0.6);
        assertThat(oaConflict.get("severity").asText()).isEqualTo("warn");

        // Verify Central Apnea Index Conflict
        ObjectNode caConflict = findConflictByMetric(conflicts, "central_apnea_index");
        assertThat(caConflict).isNotNull();
        assertThat(caConflict.get("sleephq_value").asDouble()).isEqualTo(0.3);
        assertThat(caConflict.get("oscar_value").asDouble()).isEqualTo(0.5);
        assertThat(caConflict.get("delta").asDouble()).isEqualTo(0.2);
        assertThat(caConflict.get("severity").asText()).isEqualTo("info");

        // Verify Hypopnea Index Conflict
        ObjectNode hConflict = findConflictByMetric(conflicts, "hypopnea_index");
        assertThat(hConflict).isNotNull();
        assertThat(hConflict.get("sleephq_value").asDouble()).isEqualTo(0.5);
        assertThat(hConflict.get("oscar_value").asDouble()).isEqualTo(2.0);
        assertThat(hConflict.get("delta").asDouble()).isEqualTo(1.5);
        assertThat(hConflict.get("severity").asText()).isEqualTo("critical");

        // Verify Pressure Conflict
        ObjectNode presConflict = findConflictByMetric(conflicts, "average_pressure");
        assertThat(presConflict).isNotNull();
        assertThat(presConflict.get("sleephq_value").asDouble()).isEqualTo(9.5);
        assertThat(presConflict.get("oscar_value").asDouble()).isEqualTo(11.0);
        assertThat(presConflict.get("delta").asDouble()).isEqualTo(1.5);
        assertThat(presConflict.get("severity").asText()).isEqualTo("warn");

        // Verify Leak Conflict
        ObjectNode leakConflict = findConflictByMetric(conflicts, "average_leak");
        assertThat(leakConflict).isNotNull();
        assertThat(leakConflict.get("sleephq_value").asDouble()).isEqualTo(2.1);
        assertThat(leakConflict.get("oscar_value").asDouble()).isEqualTo(8.5);
        assertThat(leakConflict.get("delta").asDouble()).isEqualTo(6.4);
        assertThat(leakConflict.get("severity").asText()).isEqualTo("warn");
    }

    private ObjectNode findConflictByMetric(ArrayNode conflicts, String metric) {
        for (int i = 0; i < conflicts.size(); i++) {
            ObjectNode conflict = (ObjectNode) conflicts.get(i);
            if (conflict.get("metric").asText().equals(metric)) {
                return conflict;
            }
        }
        return null;
    }
}
