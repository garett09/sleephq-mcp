package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VentilationSummarySupportTest {

    @Test
    void metricSummary_averagesEachStatOverNightsWithData() {
        ObjectNode out = VentilationSummarySupport.metricSummary(List.of(
                new VentilationSummarySupport.NightTriple(28.0, 21.0, 17.0),
                new VentilationSummarySupport.NightTriple(26.0, 19.0, 17.0)),
                "sleephq_resp_rate_summary", 0);
        assertThat(out.path("max_avg").asDouble()).isEqualTo(27.0);
        assertThat(out.path("p95_avg").asDouble()).isEqualTo(20.0);
        assertThat(out.path("median_avg").asDouble()).isEqualTo(17.0);
        assertThat(out.path("nights_used").asInt()).isEqualTo(2);
        assertThat(out.path("source").asText()).isEqualTo("sleephq_resp_rate_summary");
    }

    @Test
    void metricSummary_excludesNaNNightsAndRoundsToDecimals() {
        ObjectNode out = VentilationSummarySupport.metricSummary(List.of(
                new VentilationSummarySupport.NightTriple(12.4, 7.8, 6.25),
                new VentilationSummarySupport.NightTriple(Double.NaN, Double.NaN, Double.NaN)),
                "oscar_pld", 1);
        assertThat(out.path("nights_used").asInt()).isEqualTo(1);
        assertThat(out.path("median_avg").asDouble()).isEqualTo(6.3);
    }

    @Test
    void metricSummary_returnsNullWhenNoUsableNights() {
        ObjectNode out = VentilationSummarySupport.metricSummary(List.of(
                new VentilationSummarySupport.NightTriple(Double.NaN, Double.NaN, Double.NaN)),
                "oscar_pld", 0);
        assertThat(out).isNull();
    }

    @Test
    void respiratoryRateFromSleepHq_readsMaxUpperMedSkippingSkippedNights() {
        ArrayNode nights = JsonApi.mapper().createArrayNode();
        ObjectNode n1 = nights.addObject();
        n1.put("date", "2026-05-27");
        n1.putObject("data").putObject("attributes").putObject("resp_rate_summary")
                .put("max", 28.4).put("upper", 21.0).put("med", 16.8);
        ObjectNode skipped = nights.addObject();
        skipped.put("date", "2026-05-21").put("skipped", true);

        ObjectNode rr = VentilationSummarySupport.respiratoryRateFromSleepHq(nights);
        assertThat(rr.path("max_avg").asDouble()).isEqualTo(28.0);
        assertThat(rr.path("nights_used").asInt()).isEqualTo(1);
        assertThat(rr.path("source").asText()).isEqualTo("sleephq_resp_rate_summary");
    }

    @Test
    void fromOscarChannels_buildsTvMvRrFromChannelsMaxP95Median() {
        ObjectNode night = JsonApi.mapper().createObjectNode();
        ObjectNode channels = night.putObject("channels");
        channels.putObject("tidal_volume").put("max", 712.0).put("p95", 466.0).put("median", 364.0);
        channels.putObject("minute_vent").put("max", 12.4).put("p95", 7.8).put("median", 6.2);
        channels.putObject("resp_rate").put("max", 27.0).put("p95", 20.0).put("median", 17.0);

        ObjectNode vent = VentilationSummarySupport.fromOscarChannels(List.of(night));
        assertThat(vent.path("tidal_volume_ml").path("median_avg").asDouble()).isEqualTo(364.0);
        assertThat(vent.path("minute_vent_l_min").path("median_avg").asDouble()).isEqualTo(6.2);
        assertThat(vent.path("respiratory_rate_per_min").path("source").asText()).isEqualTo("oscar_pld");
    }

    @Test
    void fromOscarChannels_returnsNullWhenNoVentilationChannels() {
        ObjectNode night = JsonApi.mapper().createObjectNode();
        night.putObject("channels").putObject("pressure").put("max", 11.0).put("p95", 10.8);
        assertThat(VentilationSummarySupport.fromOscarChannels(List.of(night))).isNull();
    }

    @Test
    void fromOscarChannels_excludesSummaryOnlyNightsLackingMedian() {
        ObjectNode pldNight = JsonApi.mapper().createObjectNode();
        pldNight.putObject("channels").putObject("tidal_volume")
                .put("max", 1260.0).put("p95", 480.0).put("median", 380.0);

        ObjectNode summaryOnlyNight = JsonApi.mapper().createObjectNode();
        // summary-only: max/p95 present as 0.0, NO median key
        summaryOnlyNight.putObject("channels").putObject("tidal_volume")
                .put("max", 0.0).put("p95", 0.0);

        ObjectNode vent = VentilationSummarySupport.fromOscarChannels(
                java.util.List.of(pldNight, summaryOnlyNight));
        ObjectNode tv = (ObjectNode) vent.get("tidal_volume_ml");
        assertThat(tv.path("nights_used").asInt()).isEqualTo(1);
        assertThat(tv.path("median_avg").asDouble()).isEqualTo(380.0);
        assertThat(tv.path("p95_avg").asDouble()).isEqualTo(480.0);
        assertThat(tv.path("max_avg").asDouble()).isEqualTo(1260.0);
        // sanity: 95th percentile must never be below the median
        assertThat(tv.path("p95_avg").asDouble()).isGreaterThanOrEqualTo(tv.path("median_avg").asDouble());
    }
}
