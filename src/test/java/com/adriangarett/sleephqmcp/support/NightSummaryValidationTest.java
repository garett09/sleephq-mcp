package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.NightChannelSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NightSummaryValidationTest {

    @Test
    void build_comparesP95AndMedian_andSetsAgreeWithinTolerance() {
        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        channels.put("pressure", new NightChannelSummary("cmH2O", 11.2, 10.6, 8.4, 4, 12, 8.6, 100, null));
        JsonNode cpapAttrs = JsonApi.parse("{ \"pressure_summary\": { \"upper\": 10.5, \"med\": 8.4 } }");

        ObjectNode result = NightSummaryValidation.build(channels, cpapAttrs, null);

        JsonNode p = result.path("pressure");
        assertThat(p.path("our_p95").asDouble()).isEqualTo(10.6);
        assertThat(p.path("sleephq_p95").asDouble()).isEqualTo(10.5);
        assertThat(p.path("agree").asBoolean()).isTrue();
    }

    @Test
    void build_flagsDisagreement_whenBeyondTolerance() {
        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        channels.put("leak_rate", new NightChannelSummary("L/min", 22, 18.0, 2, 0, 38, 4, 100, null));
        JsonNode cpapAttrs = JsonApi.parse("{ \"leak_rate_summary\": { \"upper\": 8.0, \"med\": 2.0 } }");

        ObjectNode result = NightSummaryValidation.build(channels, cpapAttrs, null);

        assertThat(result.path("leak_rate").path("agree").asBoolean()).isFalse();
    }

    @Test
    void build_returnsNull_whenNoChannelHasSummary() {
        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        channels.put("snore", new NightChannelSummary("", 1, 0.5, 0.1, 0, 1, 0.2, 100, null));
        assertThat(NightSummaryValidation.build(channels, JsonApi.parse("{}"), null)).isNull();
    }
}
