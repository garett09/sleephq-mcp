package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds AirView-style span aggregates (Maximum / 95th / Median, each averaged over days-used)
 * for ventilation metrics. RR comes from SleepHQ {@code resp_rate_summary}; TV/MV come from OSCAR
 * PLD channel stats. Numbers are pre-rounded here so prompts can copy them verbatim.
 */
public final class VentilationSummarySupport {

    private VentilationSummarySupport() {}

    /** One night's (max, 95th, median) for one metric; any element {@code NaN} = missing that night. */
    public record NightTriple(double max, double p95, double median) {}

    /**
     * @return {@code {max_avg, p95_avg, median_avg, nights_used, source}} or {@code null} when no
     *         night carried any of the three stats.
     */
    public static ObjectNode metricSummary(List<NightTriple> nights, String source, int decimals) {
        List<Double> maxes = new ArrayList<>();
        List<Double> p95s = new ArrayList<>();
        List<Double> medians = new ArrayList<>();
        int used = 0;
        for (NightTriple t : nights) {
            boolean any = false;
            if (!Double.isNaN(t.max())) { maxes.add(t.max()); any = true; }
            if (!Double.isNaN(t.p95())) { p95s.add(t.p95()); any = true; }
            if (!Double.isNaN(t.median())) { medians.add(t.median()); any = true; }
            if (any) {
                used++;
            }
        }
        if (used == 0) {
            return null;
        }
        ObjectNode out = JsonApi.mapper().createObjectNode();
        putAvg(out, "max_avg", maxes, decimals);
        putAvg(out, "p95_avg", p95s, decimals);
        putAvg(out, "median_avg", medians, decimals);
        out.put("nights_used", used);
        out.put("source", source);
        return out;
    }

    /** RR aggregate from {@code get-comparison} {@code nights[].data.attributes.resp_rate_summary}. */
    public static ObjectNode respiratoryRateFromSleepHq(ArrayNode nights) {
        List<NightTriple> rr = new ArrayList<>();
        for (JsonNode night : nights) {
            if (night.path("skipped").asBoolean(false)) {
                continue;
            }
            JsonNode s = night.path("data").path("attributes").path("resp_rate_summary");
            if (!s.isObject() || s.isEmpty()) {
                continue;
            }
            rr.add(new NightTriple(num(s, "max"), num(s, "upper"), num(s, "med")));
        }
        return metricSummary(rr, "sleephq_resp_rate_summary", 0);
    }

    /** TV/MV/RR aggregate from OSCAR full night nodes ({@code channels.*.{max,p95,median}}). */
    public static ObjectNode fromOscarChannels(Collection<ObjectNode> fullNights) {
        List<NightTriple> tv = new ArrayList<>();
        List<NightTriple> mv = new ArrayList<>();
        List<NightTriple> rr = new ArrayList<>();
        for (ObjectNode night : fullNights) {
            JsonNode ch = night.path("channels");
            addTriple(tv, ch.path("tidal_volume"));
            addTriple(mv, ch.path("minute_vent"));
            addTriple(rr, ch.path("resp_rate"));
        }
        ObjectNode vent = JsonApi.mapper().createObjectNode();
        putIfPresent(vent, "tidal_volume_ml", metricSummary(tv, "oscar_pld", 0));
        putIfPresent(vent, "minute_vent_l_min", metricSummary(mv, "oscar_pld", 1));
        putIfPresent(vent, "respiratory_rate_per_min", metricSummary(rr, "oscar_pld", 0));
        return vent.isEmpty() ? null : vent;
    }

    private static void addTriple(List<NightTriple> list, JsonNode ch) {
        if (!ch.isObject() || ch.isEmpty()) {
            return;
        }
        list.add(new NightTriple(num(ch, "max"), num(ch, "p95"), num(ch, "median")));
    }

    private static void putAvg(ObjectNode out, String key, List<Double> values, int decimals) {
        if (values.isEmpty()) {
            return;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        double avg = sum / values.size();
        double factor = Math.pow(10, decimals);
        out.put(key, Math.round(avg * factor) / factor);
    }

    private static void putIfPresent(ObjectNode parent, String key, ObjectNode value) {
        if (value != null) {
            parent.set(key, value);
        }
    }

    private static double num(JsonNode obj, String key) {
        JsonNode n = obj.path(key);
        return n.isNumber() ? n.asDouble() : Double.NaN;
    }
}
