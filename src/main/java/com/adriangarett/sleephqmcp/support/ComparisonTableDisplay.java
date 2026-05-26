package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Agent-friendly per-night display fields for rolling comparison tables (balanced / weekly-trend).
 */
public final class ComparisonTableDisplay {

    private static final String[] SPO2_MIN_KEYS = {"mn", "min", "lower"};
    private static final String[] PERCENTILE_95_KEYS = {"95", "p95", "ninety_five", "95th"};
    private static final String[] AVG_KEYS = {"av", "avg", "average"};
    private static final String[] AHI_COMPONENT_KEYS = {"oa", "ca", "h", "re", "ua", "ar", "rera"};
    private static final String[] AHI_ALL_KEYS = {"av", "avg", "average", "oa", "ca", "h", "re", "ua", "ar", "rera"};

    private ComparisonTableDisplay() {
    }

    /**
     * Adds {@code table_display} to a comparison night row when {@code data} is present.
     */
    public static void attachIfPresent(ObjectNode row) {
        if (row == null || row.path("skipped").asBoolean(false)) {
            return;
        }
        JsonNode data = row.path("data");
        if (!data.isObject()) {
            return;
        }
        JsonNode attrs = data.path("attributes");
        if (!attrs.isObject()) {
            return;
        }
        row.set("table_display", build(attrs, row.path("journal")));
    }

    static ObjectNode build(JsonNode attrs, JsonNode journal) {
        ObjectNode display = JsonApi.mapper().createObjectNode();
        attachTherapySummaries(display, attrs);
        attachMachineSettings(display, attrs.path("machine_settings"));

        ObjectNode spo2 = display.putObject("spo2_pct");
        putIfPresent(spo2, "avg", readSummaryValue(attrs.path("spo2_summary"), "av"));
        putIfPresent(spo2, "min", readSummaryValue(attrs.path("spo2_summary"), SPO2_MIN_KEYS));
        if (spo2.isEmpty()) {
            display.remove("spo2_pct");
        }

        attachJournal(display, journal);
        display.put("usage_cell", buildUsageCell(display.path("usage_hours")));
        display.put("resp_rate_cell", buildRespRateCell(display.path("resp_rate_per_min")));
        display.put("flow_limit_cell", buildFlowLimitCell(display.path("flow_limit")));
        display.put("spo2_cell", buildSpo2Cell(display.path("spo2_pct")));
        display.put("pulse_cell", buildPulseCell(display.path("pulse_bpm")));
        display.put("sleep_cell", buildSleepCell(display.path("sleep_minutes")));
        display.put("journal_cell", buildJournalCell(journal));
        display.put("settings_cell", buildSettingsCell(attrs.path("machine_settings")));
        // HTML sub-tables break Goose/chat pipe-table renderers — prefer *_cell fields in markdown tables.
        display.put("spo2_nested_table_html", buildSpo2Html(display.path("spo2_pct")));
        display.put("sleep_nested_table_html", buildSleepHtml(display.path("sleep_minutes")));
        return display;
    }

    private static void attachTherapySummaries(ObjectNode display, JsonNode attrs) {
        Double usageHours = readUsageHours(attrs);
        putIfPresent(display, "usage_hours", usageHours);

        JsonNode ahiSummary = attrs.path("ahi_summary");
        attachSummaryObject(display, "ahi", ahiSummary, AHI_ALL_KEYS);
        display.put("ahi_cell", buildAhiCell(ahiSummary));
        display.put("osa_cell", buildOaCell(ahiSummary));
        display.put("csa_cell", buildCaCell(ahiSummary));
        display.put("h_cell", buildHypopneaCell(ahiSummary));
        String apneaIndices = buildApneaIndicesCell(ahiSummary);
        if (!apneaIndices.isEmpty()) {
            display.put("apnea_indices_cell", apneaIndices);
        }
        AhiSummarySupport.readComponents(ahiSummary).ifPresent(components -> {
            if (AhiSummarySupport.isOaElevated(components.oaPerHr())) {
                display.put("osa_elevated", true);
            }
            if (AhiSummarySupport.isCaElevated(components.caPerHr())) {
                display.put("csa_elevated", true);
            }
        });

        attachSummaryObject(display, "pressure_cmh2o", attrs.path("pressure_summary"),
                concatKeys(AVG_KEYS, PERCENTILE_95_KEYS));
        display.put("pressure_cell", buildPressureCell(display.path("pressure_cmh2o")));

        attachSummaryObject(display, "epap_cmh2o", attrs.path("epap_summary"), concatKeys(AVG_KEYS, PERCENTILE_95_KEYS));
        display.put("epap_cell", buildEpapCell(display.path("epap_cmh2o")));

        Double leak95 = readLeak95(attrs);
        putIfPresent(display, "leak_95_l_min", leak95);
        Integer largeLeakMinutes = attrs.path("large_leak").isNumber() ? attrs.path("large_leak").asInt() : null;
        if (largeLeakMinutes != null) {
            display.put("large_leak_minutes", largeLeakMinutes);
        }
        display.put("leak_cell", buildLeakCell(leak95, largeLeakMinutes));

        attachSummaryObject(display, "resp_rate_per_min", attrs.path("resp_rate_summary"),
                concatKeys(AVG_KEYS, PERCENTILE_95_KEYS));
        attachSummaryObject(display, "flow_limit", attrs.path("flow_limit_summary"), concatKeys(AVG_KEYS, PERCENTILE_95_KEYS));

        ObjectNode pulse = display.putObject("pulse_bpm");
        putIfPresent(pulse, "avg", readSummaryValue(attrs.path("pulse_rate_summary"), AVG_KEYS));
        putIfPresent(pulse, "min", readSummaryValue(attrs.path("pulse_rate_summary"), SPO2_MIN_KEYS));
        if (pulse.isEmpty()) {
            display.remove("pulse_bpm");
        }

        markTherapySummariesPresent(display, attrs);
    }

    private static void attachSummaryObject(ObjectNode display, String field, JsonNode summary, String... keys) {
        if (!summary.isObject() || summary.isEmpty()) {
            return;
        }
        ObjectNode out = display.putObject(field);
        for (String key : keys) {
            putIfPresent(out, key, readSummaryValue(summary, key));
        }
        if (out.isEmpty()) {
            display.remove(field);
        }
    }

    private static void markTherapySummariesPresent(ObjectNode display, JsonNode attrs) {
        ArrayNode present = display.putArray("therapy_summaries_present");
        appendIfPresent(present, attrs, "ahi_summary");
        appendIfPresent(present, attrs, "pressure_summary");
        appendIfPresent(present, attrs, "leak_rate_summary");
        appendIfPresent(present, attrs, "flow_limit_summary");
        appendIfPresent(present, attrs, "resp_rate_summary");
        appendIfPresent(present, attrs, "epap_summary");
        appendIfPresent(present, attrs, "machine_settings");
        if (attrs.path("large_leak").isNumber()) {
            present.add("large_leak");
        }
        if (attrs.path("usage").isNumber() || attrs.path("usage").isTextual()) {
            present.add("usage");
        }
    }

    private static void appendIfPresent(ArrayNode present, JsonNode attrs, String field) {
        JsonNode node = attrs.path(field);
        if (node.isObject() && !node.isEmpty()) {
            present.add(field);
        }
    }

    private static String[] concatKeys(String[]... groups) {
        return Stream.of(groups).flatMap(Arrays::stream).toArray(String[]::new);
    }

    private static void attachMachineSettings(ObjectNode display, JsonNode settings) {
        if (!settings.isObject() || settings.isEmpty()) {
            return;
        }
        ObjectNode menu = display.putObject("machine_settings");
        putIfPresent(menu, "mode", readText(settings.path("mode")));
        putIfPresent(menu, "pressure_cmh2o", readSummaryValue(settings, "pressure"));
        putIfPresent(menu, "min_pressure_cmh2o", readSummaryValue(settings, "min_pressure", "minimum_pressure"));
        putIfPresent(menu, "max_pressure_cmh2o", readSummaryValue(settings, "max_pressure", "maximum_pressure"));
        putIfPresent(menu, "epr", readText(settings.path("epr")));
        putIfPresent(menu, "epr_level", readSummaryValue(settings, "epr_level", "level"));
        putIfPresent(menu, "ramp", readText(settings.path("ramp")));
        if (menu.isEmpty()) {
            display.remove("machine_settings");
        }
    }

    /**
     * Compares consecutive nights (chronological) and sets {@code settings_changed_from_prior_night} on each row's
     * {@code table_display} when {@code machine_settings} differs from the previous non-skipped night.
     */
    public static void markSettingsChanges(ArrayNode nights) {
        if (nights == null || nights.isEmpty()) {
            return;
        }
        java.util.List<ObjectNode> chronological = new java.util.ArrayList<>();
        for (JsonNode node : nights) {
            if (node.isObject()) {
                chronological.add((ObjectNode) node);
            }
        }
        chronological.sort(java.util.Comparator.comparing(n -> n.path("date").asText("")));
        JsonNode priorSettings = null;
        for (ObjectNode row : chronological) {
            if (row.path("skipped").asBoolean(false)) {
                continue;
            }
            JsonNode settings = row.path("data").path("attributes").path("machine_settings");
            if (!settings.isObject() || settings.isEmpty()) {
                continue;
            }
            ObjectNode display = row.path("table_display").isObject()
                    ? (ObjectNode) row.get("table_display")
                    : row.putObject("table_display");
            if (priorSettings != null && !settings.equals(priorSettings)) {
                display.put("settings_changed_from_prior_night", true);
            }
            priorSettings = settings;
        }
    }

    private static void attachJournal(ObjectNode display, JsonNode journal) {
        if (journal == null || !journal.isObject()) {
            return;
        }
        putIfPresent(display, "steps", journal.path("step_count").isNumber()
                ? journal.path("step_count").asLong()
                : null);

        JsonNode summary = journal.path("sleep_stages_summary");
        if (!summary.isObject()) {
            return;
        }
        ObjectNode sleep = display.putObject("sleep_minutes");
        putIfPresent(sleep, "asleep", summary.path("asleep_minutes").isNumber()
                ? summary.path("asleep_minutes").asDouble()
                : null);
        JsonNode byStage = summary.path("minutes_by_stage");
        if (byStage.isObject()) {
            putIfPresent(sleep, "deep", stageMinutes(byStage, "deep"));
            putIfPresent(sleep, "rem", stageMinutes(byStage, "rem"));
            putIfPresent(sleep, "core", stageMinutes(byStage, "core"));
            putIfPresent(sleep, "awake", stageMinutes(byStage, "awake"));
        }
        if (sleep.isEmpty()) {
            display.remove("sleep_minutes");
        }
    }

    private static Double stageMinutes(JsonNode byStage, String label) {
        JsonNode node = byStage.path(label);
        return node.isNumber() ? node.asDouble() : null;
    }

    /**
     * SleepHQ {@code machine_date.usage} is usually therapy duration in <strong>seconds</strong> (e.g. 25440 → ~7.1 h).
     * Values already in hours are typically &lt; 24 (e.g. 8.8).
     */
    private static final double MAX_USAGE_AS_HOURS = 24.0;

    private static Double readUsageHours(JsonNode attrs) {
        Double raw = parseUsageRaw(attrs.path("usage"));
        if (raw == null) {
            return null;
        }
        if (raw > MAX_USAGE_AS_HOURS) {
            return raw / 3600.0;
        }
        return raw;
    }

    private static Double parseUsageRaw(JsonNode usage) {
        if (usage.isNumber()) {
            return usage.asDouble();
        }
        if (usage.isTextual()) {
            String text = usage.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(text.replace("h", "").trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double readLeak95(JsonNode attrs) {
        if (attrs.path("leak_95th").isNumber()) {
            return attrs.path("leak_95th").asDouble();
        }
        return readSummaryValue(attrs.path("leak_rate_summary"), PERCENTILE_95_KEYS);
    }

    private static Double readSummaryValue(JsonNode summary, String... keys) {
        if (!summary.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode node = summary.path(key);
            if (node.isNumber()) {
                return node.asDouble();
            }
        }
        return null;
    }

    private static String readText(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private static void putIfPresent(ObjectNode target, String field, Double value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode target, String field, Long value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode target, String field, String value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    /**
     * Plain-text SpO₂ for a single markdown pipe-table cell (no HTML).
     */
    static String buildUsageCell(JsonNode usageHours) {
        if (!usageHours.isNumber()) {
            return "";
        }
        double hours = usageHours.asDouble();
        if (hours == Math.rint(hours)) {
            return ((long) hours) + " h";
        }
        return (Math.round(hours * 10.0) / 10.0) + " h";
    }

    /**
     * Single wide table cell for OSA + CSA + total AHI (preferred over three narrow columns in markdown).
     */
    static String buildApneaIndicesCell(JsonNode ahiSummary) {
        if (!ahiSummary.isObject() || ahiSummary.isEmpty()) {
            return "";
        }
        Double total = readSummaryValue(ahiSummary, AVG_KEYS);
        Double oa = readSummaryValue(ahiSummary, "oa");
        Double ca = readSummaryValue(ahiSummary, "ca");
        if (total == null && oa == null && ca == null) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        appendApneaIndexPart(cell, "OSA", oa, AhiSummarySupport.isOaElevated(oa));
        appendApneaIndexPart(cell, "CSA", ca, AhiSummarySupport.isCaElevated(ca));
        appendApneaIndexPart(cell, "H", readSummaryValue(ahiSummary, "h"), false);
        appendApneaIndexPart(cell, "AHI", total, false);
        return cell.toString();
    }

    static String buildAhiCell(JsonNode ahiSummary) {
        if (!ahiSummary.isObject() || ahiSummary.isEmpty()) {
            return "";
        }
        Double total = readSummaryValue(ahiSummary, AVG_KEYS);
        if (total == null) {
            return "";
        }
        boolean splitColumns = readSummaryValue(ahiSummary, "oa") != null
                || readSummaryValue(ahiSummary, "ca") != null;
        if (splitColumns) {
            return formatApneaIndexPerHr(total);
        }
        StringBuilder cell = new StringBuilder(formatApneaIndexPerHr(total));
        for (String key : AHI_COMPONENT_KEYS) {
            Double component = readSummaryValue(ahiSummary, key);
            if (component == null) {
                continue;
            }
            cell.append(" · ").append(key.toUpperCase()).append(' ').append(formatApneaIndexPerHr(component));
        }
        return cell.toString();
    }

    /** Obstructive apnea index (OSA residual on therapy), events/hr. */
    static String buildOaCell(JsonNode ahiSummary) {
        return buildSingleAhiIndexCell(ahiSummary, "oa");
    }

    /** Central apnea index (CSA / TECSA signal), events/hr. */
    static String buildCaCell(JsonNode ahiSummary) {
        return buildSingleAhiIndexCell(ahiSummary, "ca");
    }

    /** Hypopnea index (H), events/hr — often explains AHI above OSA + CSA alone. */
    static String buildHypopneaCell(JsonNode ahiSummary) {
        return buildSingleAhiIndexCell(ahiSummary, "h");
    }

    private static void appendApneaIndexPart(StringBuilder cell, String label, Double perHr, boolean elevated) {
        if (perHr == null) {
            return;
        }
        if (cell.length() > 0) {
            cell.append(" · ");
        }
        cell.append(label).append(' ').append(formatApneaIndexPerHr(perHr));
        if (elevated) {
            cell.append('!');
        }
    }

    private static String buildSingleAhiIndexCell(JsonNode ahiSummary, String key) {
        if (!ahiSummary.isObject() || ahiSummary.isEmpty()) {
            return "";
        }
        Double value = readSummaryValue(ahiSummary, key);
        if (value == null) {
            return "";
        }
        return formatApneaIndexPerHr(value);
    }

    static String formatApneaIndexPerHr(double indexPerHr) {
        return formatAhi(indexPerHr) + "/hr";
    }

    static String buildPressureCell(JsonNode pressure) {
        return buildAvgP95Cell(pressure, "cmH₂O", ComparisonTableDisplay::formatPressure);
    }

    static String buildEpapCell(JsonNode epap) {
        return buildAvgP95Cell(epap, "cmH₂O", ComparisonTableDisplay::formatPressure);
    }

    private static String buildAvgP95Cell(JsonNode summary, String unit, java.util.function.DoubleFunction<String> formatter) {
        if (!summary.isObject() || summary.isEmpty()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        Double avg = readSummaryValue(summary, AVG_KEYS);
        if (avg != null) {
            cell.append("avg ").append(formatter.apply(avg)).append(' ').append(unit);
        }
        Double p95 = readSummaryValue(summary, PERCENTILE_95_KEYS);
        if (p95 != null) {
            if (cell.length() > 0) {
                cell.append(" / 95th ");
            } else {
                cell.append("95th ");
            }
            cell.append(formatter.apply(p95)).append(' ').append(unit);
        }
        return cell.toString();
    }

    static String buildLeakCell(Double leak95LMin, Integer largeLeakMinutes) {
        if (leak95LMin == null && (largeLeakMinutes == null || largeLeakMinutes <= 0)) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        if (leak95LMin != null) {
            cell.append(formatLeak(leak95LMin));
        }
        if (largeLeakMinutes != null && largeLeakMinutes > 0) {
            if (cell.length() > 0) {
                cell.append(" · ");
            }
            cell.append("large ").append(largeLeakMinutes).append("m");
        }
        return cell.toString();
    }

    static String buildLeakCell(Double leak95LMin) {
        return buildLeakCell(leak95LMin, null);
    }

    static String buildLeakCellFromAttributes(JsonNode attrs) {
        if (attrs == null || !attrs.isObject()) {
            return "";
        }
        Double leak95 = readLeak95(attrs);
        Integer largeLeakMinutes = attrs.path("large_leak").isNumber() ? attrs.path("large_leak").asInt() : null;
        return buildLeakCell(leak95, largeLeakMinutes);
    }

    static String formatLeak(double leak95LMin) {
        if (leak95LMin == Math.rint(leak95LMin)) {
            return ((long) leak95LMin) + " L/min";
        }
        return (Math.round(leak95LMin * 10.0) / 10.0) + " L/min";
    }

    static String buildPulseCell(JsonNode pulse) {
        if (!pulse.isObject() || pulse.isEmpty()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        if (pulse.path("avg").isNumber()) {
            cell.append("avg ").append(formatBpm(pulse.path("avg").asDouble()));
        }
        if (pulse.path("min").isNumber()) {
            if (cell.length() > 0) {
                cell.append(" / min ");
            } else {
                cell.append("min ");
            }
            cell.append(formatBpm(pulse.path("min").asDouble()));
        }
        return cell.toString();
    }

    static String buildRespRateCell(JsonNode resp) {
        if (!resp.isObject() || resp.isEmpty()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        Double avg = readSummaryValue(resp, AVG_KEYS);
        if (avg != null) {
            cell.append("avg ").append(formatRate(avg));
        }
        Double p95 = readSummaryValue(resp, PERCENTILE_95_KEYS);
        if (p95 != null) {
            if (cell.length() > 0) {
                cell.append(" / 95th ");
            }
            cell.append(formatRate(p95));
        }
        return cell.toString();
    }

    static String buildFlowLimitCell(JsonNode flowLimit) {
        if (!flowLimit.isObject() || flowLimit.isEmpty()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        Double p95 = readSummaryValue(flowLimit, PERCENTILE_95_KEYS);
        if (p95 != null) {
            cell.append("95th ").append(formatFlowLimit(p95));
        } else {
            Double avg = readSummaryValue(flowLimit, AVG_KEYS);
            if (avg != null) {
                cell.append("avg ").append(formatFlowLimit(avg));
            }
        }
        return cell.toString();
    }

    private static String formatBpm(double bpm) {
        if (bpm == Math.rint(bpm)) {
            return ((long) bpm) + " bpm";
        }
        return (Math.round(bpm * 10.0) / 10.0) + " bpm";
    }

    private static String formatRate(double rate) {
        if (rate == Math.rint(rate)) {
            return ((long) rate) + "/min";
        }
        return (Math.round(rate * 10.0) / 10.0) + "/min";
    }

    private static String formatFlowLimit(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    static String buildSpo2Cell(JsonNode spo2) {
        if (!spo2.isObject() || spo2.isEmpty()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        if (spo2.path("avg").isNumber()) {
            cell.append("avg ").append(formatPercent(spo2.path("avg").asDouble()));
        }
        if (spo2.path("min").isNumber()) {
            if (cell.length() > 0) {
                cell.append(" / min ");
            } else {
                cell.append("min ");
            }
            cell.append(formatPercent(spo2.path("min").asDouble()));
        }
        return cell.toString();
    }

    static String buildSleepCell(JsonNode sleep) {
        if (!sleep.isObject() || sleep.isEmpty()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        appendSleepPart(cell, "total", sleep.path("asleep"));
        appendSleepPart(cell, "light", sleep.path("core"));
        appendSleepPart(cell, "deep", sleep.path("deep"));
        appendSleepPart(cell, "rem", sleep.path("rem"));
        return cell.toString();
    }

    /**
     * Steps, feeling, notes — separate from sleep stages for pipe tables.
     */
    static String buildJournalCell(JsonNode journal) {
        if (journal == null || !journal.isObject()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        if (journal.path("step_count").isNumber()) {
            cell.append(formatCount(journal.path("step_count").asLong())).append(" steps");
        }
        if (journal.path("feeling_score").isNumber()) {
            int score = journal.path("feeling_score").asInt();
            String mood = journal.path("feeling_label").asText(null);
            appendJournalPart(cell, mood != null && !mood.isBlank()
                    ? mood + " (" + score + ")"
                    : JournalFeelingScore.displayFor(score));
        }
        String notes = readText(journal.path("notes"));
        if (notes != null) {
            String clipped = notes.length() > 80 ? notes.substring(0, 77) + "..." : notes;
            appendJournalPart(cell, "note: " + clipped);
        }
        return cell.toString();
    }

    private static void appendJournalPart(StringBuilder cell, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (cell.length() > 0) {
            cell.append(" · ");
        }
        cell.append(part);
    }

    private static void appendSleepPart(StringBuilder cell, String label, JsonNode minutes) {
        if (!minutes.isNumber()) {
            return;
        }
        if (cell.length() > 0) {
            cell.append(" · ");
        }
        cell.append(label).append(' ').append(formatMinutes(minutes.asDouble()));
    }

    static String buildSettingsCell(JsonNode settings) {
        if (!settings.isObject() || settings.isEmpty()) {
            return "";
        }
        StringBuilder cell = new StringBuilder();
        String mode = readText(settings.path("mode"));
        if (mode != null) {
            cell.append(mode);
        }
        Double pressure = readSummaryValue(settings, "pressure", "pressure_cmh2o");
        if (pressure != null) {
            appendSettingsPart(cell, formatPressure(pressure));
        } else {
            Double minP = readSummaryValue(settings, "min_pressure", "min_pressure_cmh2o", "minimum_pressure");
            Double maxP = readSummaryValue(settings, "max_pressure", "max_pressure_cmh2o", "maximum_pressure");
            if (minP != null && maxP != null) {
                appendSettingsPart(cell, formatPressure(minP) + "-" + formatPressure(maxP));
            }
        }
        String epr = readText(settings.path("epr"));
        if (epr != null) {
            appendSettingsPart(cell, "EPR " + epr);
        }
        if (settings.path("epr_level").isNumber()) {
            appendSettingsPart(cell, "L" + settings.path("epr_level").asInt());
        }
        return cell.toString();
    }

    private static void appendSettingsPart(StringBuilder cell, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (cell.length() > 0) {
            cell.append("; ");
        }
        cell.append(part);
    }

    static String buildSpo2Html(JsonNode spo2) {
        if (!spo2.isObject() || spo2.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<table><tr><th>Metric</th><th>%</th></tr>");
        appendSpo2Row(html, "Avg", spo2.path("avg"));
        appendSpo2Row(html, "Min", spo2.path("min"));
        return html.append("</table>").toString();
    }

    private static void appendSpo2Row(StringBuilder html, String label, JsonNode value) {
        if (!value.isNumber()) {
            return;
        }
        html.append("<tr><td>").append(label).append("</td><td>")
                .append(formatPercent(value.asDouble()))
                .append("</td></tr>");
    }

    private static String formatPressure(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    static String formatPercent(double value) {
        if (value == Math.rint(value)) {
            return ((long) value) + "%";
        }
        return (Math.round(value * 10.0) / 10.0) + "%";
    }

    static String buildSleepHtml(JsonNode sleep) {
        if (!sleep.isObject() || sleep.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<table><tr><th>Stage</th><th>Duration</th></tr>");
        appendRow(html, "Asleep", sleep.path("asleep"));
        appendRow(html, "Deep", sleep.path("deep"));
        appendRow(html, "REM", sleep.path("rem"));
        if (sleep.has("core")) {
            appendRow(html, "Core", sleep.path("core"));
        }
        return html.append("</table>").toString();
    }

    private static void appendRow(StringBuilder html, String label, JsonNode minutes) {
        if (!minutes.isNumber()) {
            return;
        }
        html.append("<tr><td>").append(label).append("</td><td>")
                .append(formatMinutes(minutes.asDouble()))
                .append("</td></tr>");
    }

    static String formatCount(long value) {
        return String.format("%,d", value);
    }

    static String formatAhi(double ahi) {
        String formatted = ahi < 10 ? String.format("%.2f", ahi) : String.format("%.1f", ahi);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }

    static String formatMinutes(double minutes) {
        long total = Math.round(minutes);
        long hours = total / 60;
        long mins = total % 60;
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }
}
