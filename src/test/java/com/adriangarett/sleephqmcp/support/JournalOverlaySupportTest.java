package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JournalOverlaySupportTest {

    @Test
    void buildWellnessObject_includesActiveEnergyAndParsedStages() throws Exception {
        String journals = new String(getClass().getResourceAsStream("/journal/list-journals-sample.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var attrs = JsonApi.parse(journals).path("data").get(0).path("attributes");

        ObjectNode wellness = JournalOverlaySupport.buildWellnessObject(attrs);

        assertThat(wellness.path("step_count").asInt()).isEqualTo(8421);
        assertThat(wellness.path("feeling_score").asInt()).isEqualTo(4);
        assertThat(wellness.path("feeling_label").asText()).isEqualTo("Good");
        assertThat(wellness.path("active_energy_joules").asLong()).isEqualTo(1234000L);
        assertThat(wellness.path("sleep_stages_parsed").path("stages").isArray()).isTrue();
    }

    @Test
    void enrichEnvelopeJson_attachesJournalSibling() {
        String envelope = "{\"data\":{\"id\":\"1\",\"type\":\"machine_date\",\"attributes\":{\"date\":\"2026-05-23\"}}}";
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.put("date", "2026-05-23");
        attrs.put("step_count", 100);

        String out = JournalOverlaySupport.enrichEnvelopeJson(envelope, attrs);
        var root = JsonApi.parse(out);

        assertThat(root.path("data").path("id").asText()).isEqualTo("1");
        assertThat(root.path("journal").path("step_count").asInt()).isEqualTo(100);
    }

    @Test
    void buildWellnessObject_appleHealthSegments_omitsRawStringAndAddsSummary() throws Exception {
        String segments = new String(
                getClass().getResourceAsStream("/journal/sleep-stages-apple-health-segments.json").readAllBytes(),
                StandardCharsets.UTF_8);
        var attrs = JsonApi.mapper().createObjectNode();
        attrs.put("date", "2026-05-24");
        attrs.put("step_count", 2697);
        attrs.put("sleep_stages", segments);

        ObjectNode wellness = JournalOverlaySupport.buildWellnessObject(attrs);

        assertThat(wellness.path("sleep_stages").isMissingNode()).isTrue();
        assertThat(wellness.path("sleep_stages_summary").path("minutes_by_stage").path("core").isNumber()).isTrue();
        assertThat(wellness.path("sleep_stages_parsed").isArray()).isTrue();
    }

    @Test
    void enrichEnvelopeJson_omitsJournalWhenNoAttributes() {
        String envelope = "{\"data\":{\"id\":\"1\",\"type\":\"machine_date\",\"attributes\":{\"date\":\"2026-05-23\"}}}";
        String out = JournalOverlaySupport.enrichEnvelopeJson(envelope, null);
        assertThat(JsonApi.parse(out).path("journal").isMissingNode()).isTrue();
    }
}
