package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NightTherapyDisplaySupportTest {

    @Test
    void attachIfPresent_addsApneaBreakdownAndIndicesCell() {
        ObjectNode envelope = JsonApi.mapper().createObjectNode();
        ObjectNode data = envelope.putObject("data");
        data.put("id", "md-1");
        data.put("type", "machine_date");
        ObjectNode attrs = data.putObject("attributes");
        attrs.put("usage", 8.8);
        ObjectNode ahi = attrs.putObject("ahi_summary");
        ahi.put("av", 0.57);
        ahi.put("oa", 0.2);
        ahi.put("ca", 0.1);
        ahi.put("h", 0.27);

        NightTherapyDisplaySupport.attachIfPresent(envelope);

        assertThat(envelope.path("therapy_display").path("apnea_indices_cell").asText())
                .isEqualTo("OSA 0.2/hr · CSA 0.1/hr · H 0.27/hr · AHI 0.57/hr");
        assertThat(envelope.path("ahi_components").path("oa_per_hr").asDouble()).isEqualTo(0.2);
        assertThat(envelope.path("ahi_components").path("h_per_hr").asDouble()).isEqualTo(0.27);
        assertThat(envelope.path("therapy_display").path("apnea").path("hypopnea_per_hr").asDouble()).isEqualTo(0.27);
    }

    @Test
    void attachIfPresent_usageInSeconds_formatsUsageCellAsHours() {
        ObjectNode envelope = JsonApi.mapper().createObjectNode();
        ObjectNode data = envelope.putObject("data");
        data.put("type", "machine_date");
        ObjectNode attrs = data.putObject("attributes");
        attrs.put("usage", 25440);
        attrs.putObject("ahi_summary").put("av", 0.57);

        NightTherapyDisplaySupport.attachIfPresent(envelope);

        assertThat(envelope.path("therapy_display").path("usage_cell").asText()).isEqualTo("7.1 h");
    }

    @Test
    void attachIfPresent_noData_leavesEnvelopeUnchanged() {
        ObjectNode envelope = JsonApi.mapper().createObjectNode();
        envelope.putNull("data");

        NightTherapyDisplaySupport.attachIfPresent(envelope);

        assertThat(envelope.has("therapy_display")).isFalse();
    }
}
