package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonApneaTrendSupportTest {

    @Test
    void attach_detectsRisingCa_andOverTitrationSignal() {
        ObjectNode root = JsonApi.mapper().createObjectNode();
        ArrayNode nights = root.putArray("nights");
        nights.add(night("2026-05-01", 0.5, 0.1, 0.2));
        nights.add(night("2026-05-02", 0.6, 0.1, 0.3));
        nights.add(night("2026-05-03", 0.7, 0.1, 0.4));
        nights.add(night("2026-05-04", 0.8, 0.1, 0.5));
        nights.add(night("2026-05-05", 1.0, 0.1, 4.0));
        nights.add(night("2026-05-06", 1.2, 0.1, 5.5));
        nights.add(night("2026-05-07", 1.5, 0.1, 6.0));
        nights.add(night("2026-05-08", 1.8, 0.1, 8.0));

        ComparisonApneaTrendSupport.attach(root, nights);

        assertThat(root.path("apnea_trends").path("ca").path("rising").asBoolean()).isTrue();
        assertThat(root.path("apnea_trends").path("pressure_signals").path("possible_over_titration").asBoolean())
                .isTrue();
        assertThat(root.path("apnea_trends").path("nights_above_threshold").size()).isGreaterThan(0);
        assertThat(root.path("apnea_trends").path("titration_decision_support").path("suggested_pressure_action").asText())
                .isEqualTo("CONSIDER_DECREASE_1_OR_HOLD");
        assertThat(root.path("apnea_trends").path("titration_decision_support").path("span_summary_bullets").size())
                .isGreaterThan(0);
    }

    @Test
    void attach_detectsRisingOa_andUnderTitrationSignal() {
        ObjectNode root = JsonApi.mapper().createObjectNode();
        ArrayNode nights = root.putArray("nights");
        for (int i = 1; i <= 4; i++) {
            nights.add(night("2026-05-0" + i, 1.0, 0.3, 0.1));
        }
        for (int i = 5; i <= 8; i++) {
            nights.add(night("2026-05-0" + i, 2.0, 2.5, 0.1));
        }

        ComparisonApneaTrendSupport.attach(root, nights);

        assertThat(root.path("apnea_trends").path("oa").path("rising").asBoolean()).isTrue();
        assertThat(root.path("apnea_trends").path("pressure_signals").path("possible_under_titration").asBoolean())
                .isTrue();
    }

    private static ObjectNode night(String date, double ahi, double oa, double ca) {
        ObjectNode row = JsonApi.mapper().createObjectNode();
        row.put("date", date);
        row.putObject("data").putObject("attributes")
                .putObject("ahi_summary")
                .put("av", ahi)
                .put("oa", oa)
                .put("ca", ca);
        ComparisonTableDisplay.attachIfPresent(row);
        return row;
    }
}
