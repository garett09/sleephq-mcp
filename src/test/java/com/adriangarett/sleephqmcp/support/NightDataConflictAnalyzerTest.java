package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
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

        Map<String, ChannelSummary> oscarChannels = new HashMap<>();
        oscarChannels.put("AHI", new ChannelSummary(1.2, 0.0, 5.0, 1.2, null, null));
        oscarChannels.put("Pressure", new ChannelSummary(9.5, 4.0, 12.0, 9.5, null, null));
        oscarChannels.put("Leak", new ChannelSummary(2.1, 0.0, 10.0, 2.1, null, null));
        oscarChannels.put("Obstructive", new ChannelSummary(null, null, null, null, 4.0, null));
        oscarChannels.put("ClearAirway", new ChannelSummary(null, null, null, null, 3.0, null));
        oscarChannels.put("Hypopnea", new ChannelSummary(null, null, null, null, 5.0, null));

        OscarSession session = new OscarSession(
                LocalDate.now().toString(),
                12345678L,
                System.currentTimeMillis(),
                36000,
                oscarChannels,
                Map.of()
        );

        ArrayNode conflicts = NightDataConflictAnalyzer.analyze(shqAttrs, session);
        assertThat(conflicts).isEmpty();
    }

    @Test
    void analyze_withDiscrepancies_detectsConflicts() {
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

        Map<String, ChannelSummary> oscarChannels = new HashMap<>();
        oscarChannels.put("AHI", new ChannelSummary(3.5, 0.0, 10.0, 3.5, null, null));
        oscarChannels.put("Pressure", new ChannelSummary(11.0, 4.0, 15.0, 11.0, null, null));
        oscarChannels.put("Leak", new ChannelSummary(8.5, 0.0, 20.0, 8.5, null, null));
        oscarChannels.put("Obstructive", new ChannelSummary(null, null, null, null, 10.0, null));
        oscarChannels.put("ClearAirway", new ChannelSummary(null, null, null, null, 5.0, null));
        oscarChannels.put("Hypopnea", new ChannelSummary(null, null, null, null, 20.0, null));

        OscarSession session = new OscarSession(
                LocalDate.now().toString(),
                12345678L,
                System.currentTimeMillis(),
                36000,
                oscarChannels,
                Map.of()
        );

        // AHI diff=2.3 > 2.0 → critical; Pressure diff=1.5 > 1.0 → warn; Leak diff=6.4 > 5.0 → warn
        ArrayNode conflicts = NightDataConflictAnalyzer.analyze(shqAttrs, session);
        assertThat(conflicts.size()).isGreaterThanOrEqualTo(3);
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
