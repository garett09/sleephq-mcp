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
        channels.put("pressure", new NightChannelSummary("cmH2O", 11.2, 10.9, 10.6, 8.4, 4, 12, 8.6, 100, null));
        JsonNode cpapAttrs = JsonApi.parse("{ \"pressure_summary\": { \"upper\": 10.5, \"med\": 8.4 } }");

        ObjectNode result = NightSummaryValidation.build(channels, cpapAttrs, null);

        JsonNode p = result.path("pressure");
        assertThat(p.path("our_p95").asDouble()).isEqualTo(10.6);
        assertThat(p.path("sleephq_p95").asDouble()).isEqualTo(10.5);
        assertThat(p.path("agree").asBoolean()).isTrue();
    }

    @Test
    void build_leakRate_prefersLeak95thOverSummaryUpper() {
        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        channels.put("leak_rate", new NightChannelSummary("L/min", 14, 13.5, 13.0, 2, 0, 20, 4, 100, null));
        JsonNode cpapAttrs = JsonApi.parse(
                "{ \"leak_95th\": 13.2, \"leak_rate_summary\": { \"upper\": 28.8, \"med\": 2.0 } }");

        ObjectNode result = NightSummaryValidation.build(channels, cpapAttrs, null);

        JsonNode leak = result.path("leak_rate");
        assertThat(leak.path("sleephq_p95").asDouble()).isEqualTo(13.2);
        assertThat(leak.path("compared_to").asText()).isEqualTo("leak_95th");
        assertThat(leak.path("agree").asBoolean()).isTrue();
    }

    @Test
    void build_flagsDisagreement_whenBeyondTolerance() {
        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        channels.put("leak_rate", new NightChannelSummary("L/min", 22, 20.0, 18.0, 2, 0, 38, 4, 100, null));
        JsonNode cpapAttrs = JsonApi.parse("{ \"leak_rate_summary\": { \"upper\": 8.0, \"med\": 2.0 } }");

        ObjectNode result = NightSummaryValidation.build(channels, cpapAttrs, null);

        assertThat(result.path("leak_rate").path("agree").asBoolean()).isFalse();
    }

    @Test
    void build_returnsNull_whenNoChannelHasSummary() {
        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        channels.put("snore", new NightChannelSummary("", 1, 0.75, 0.5, 0.1, 0, 1, 0.2, 100, null));
        assertThat(NightSummaryValidation.build(channels, JsonApi.parse("{}"), null)).isNull();
    }
}
