package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ComparisonTableDisplayTest {

    @Test
    void build_includesSpo2AvgAndMin_andSleepStages() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.put("usage", 8.8);
        attrs.putObject("ahi_summary").put("av", 0.57).put("oa", 0.2).put("ca", 0.1).put("h", 0.27);
        attrs.putObject("pressure_summary").put("av", 10.8).put("95", 11.0);
        attrs.putObject("epap_summary").put("95", 8.5);
        attrs.put("leak_95th", 9.6);
        attrs.put("large_leak", 12);
        attrs.putObject("resp_rate_summary").put("av", 14).put("95", 18);
        attrs.putObject("flow_limit_summary").put("95", 0.42);
        attrs.putObject("pulse_rate_summary").put("av", 56).put("mn", 48);
        attrs.putObject("machine_settings").put("mode", "CPAP").put("pressure", 11.0).put("epr", "Off");
        attrs.putObject("spo2_summary").put("av", 97.2).put("mn", 84);

        ObjectNode journal = JsonApi.mapper().createObjectNode();
        journal.put("step_count", 3609);
        ObjectNode summary = journal.putObject("sleep_stages_summary");
        summary.put("asleep_minutes", 446);
        summary.putObject("minutes_by_stage").put("core", 130).put("deep", 214).put("rem", 13);
        journal.put("feeling_score", 3);
        journal.put("notes", "Hard breathing because of EPR off");

        ObjectNode display = ComparisonTableDisplay.build(attrs, journal);

        assertThat(display.path("usage_hours").asDouble()).isEqualTo(8.8);
        assertThat(display.path("usage_cell").asText()).isEqualTo("8.8 h");
        assertThat(display.path("pressure_cell").asText()).isEqualTo("avg 10.8 cmH₂O / 95th 11 cmH₂O");
        assertThat(display.path("epap_cell").asText()).isEqualTo("95th 8.5 cmH₂O");
        assertThat(display.path("ahi_cell").asText()).isEqualTo("0.57/hr");
        assertThat(display.path("osa_cell").asText()).isEqualTo("0.2/hr");
        assertThat(display.path("csa_cell").asText()).isEqualTo("0.1/hr");
        assertThat(display.path("h_cell").asText()).isEqualTo("0.27/hr");
        assertThat(display.path("apnea_indices_cell").asText())
                .isEqualTo("OSA 0.2/hr · CSA 0.1/hr · H 0.27/hr · AHI 0.57/hr");
        assertThat(display.path("osa_elevated").asBoolean()).isFalse();
        assertThat(display.path("csa_elevated").asBoolean()).isFalse();
        assertThat(display.path("spo2_pct").path("avg").asDouble()).isEqualTo(97.2);
        assertThat(display.path("spo2_pct").path("min").asDouble()).isEqualTo(84);
        assertThat(display.path("sleep_minutes").path("asleep").asDouble()).isEqualTo(446);
        assertThat(display.path("sleep_minutes").path("deep").asDouble()).isEqualTo(214);
        assertThat(display.path("spo2_cell").asText()).isEqualTo("avg 97.2% / min 84%");
        assertThat(display.path("sleep_cell").asText())
                .contains("total 7h 26m").contains("light 2h 10m").contains("deep 3h 34m").contains("rem 13m");
        assertThat(display.path("leak_cell").asText()).isEqualTo("9.6 L/min · large 12m");
        java.util.List<String> summariesPresent = new java.util.ArrayList<>();
        display.path("therapy_summaries_present").forEach(node -> summariesPresent.add(node.asText()));
        assertThat(summariesPresent)
                .contains("ahi_summary", "pressure_summary", "epap_summary", "large_leak", "usage");
        assertThat(display.path("resp_rate_cell").asText()).isEqualTo("avg 14/min / 95th 18/min");
        assertThat(display.path("flow_limit_cell").asText()).isEqualTo("95th 0.42");
        assertThat(display.path("pulse_cell").asText()).isEqualTo("avg 56 bpm / min 48 bpm");
        assertThat(display.path("journal_cell").asText())
                .contains("3,609 steps")
                .contains("Okay (3)");
        assertThat(display.path("settings_cell").asText()).isEqualTo("CPAP; 11; EPR Off");
    }

    @Test
    void build_spo2Min_fallsBackToLowerKey() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.putObject("spo2_summary").put("av", 96).put("lower", 88);

        ObjectNode display = ComparisonTableDisplay.build(attrs, null);

        assertThat(display.path("spo2_pct").path("min").asInt()).isEqualTo(88);
    }

    @Test
    void formatCount_usesThousandsSeparator() {
        assertThat(ComparisonTableDisplay.formatCount(10826)).isEqualTo("10,826");
    }

    @Test
    void formatAhi_compactTwoDecimals_forTypicalValues() {
        assertThat(ComparisonTableDisplay.formatAhi(0.57)).isEqualTo("0.57");
        assertThat(ComparisonTableDisplay.formatAhi(1.19)).isEqualTo("1.19");
        assertThat(ComparisonTableDisplay.formatAhi(2.0)).isEqualTo("2");
    }

    @Test
    void formatMinutes_underOneHour_showsMinutesOnly() {
        assertThat(ComparisonTableDisplay.formatMinutes(13)).isEqualTo("13m");
    }

    @Test
    void markSettingsChanges_flagsSecondNightWhenMachineSettingsDiffer() {
        ArrayNode nights = JsonApi.mapper().createArrayNode();
        nights.add(nightWithSettings("2026-05-17", "CPAP", 11.0, "Off"));
        nights.add(nightWithSettings("2026-05-18", "CPAP", 11.0, "Full Time"));

        ComparisonTableDisplay.markSettingsChanges(nights);

        assertThat(nights.get(0).path("table_display").path("settings_changed_from_prior_night").asBoolean()).isFalse();
        assertThat(nights.get(1).path("table_display").path("settings_changed_from_prior_night").asBoolean()).isTrue();
    }

    private static ObjectNode nightWithSettings(String date, String mode, double pressure, String epr) {
        ObjectNode row = JsonApi.mapper().createObjectNode();
        row.put("date", date);
        ObjectNode attrs = row.putObject("data").putObject("attributes");
        attrs.putObject("machine_settings").put("mode", mode).put("pressure", pressure).put("epr", epr);
        ComparisonTableDisplay.attachIfPresent(row);
        return row;
    }

    @Test
    void build_usageCell_convertsSecondsToHours_whenUsageIsLargeInteger() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.put("usage", 25440);

        ObjectNode display = ComparisonTableDisplay.build(attrs, null);

        assertThat(display.path("usage_hours").asDouble()).isCloseTo(7.066, within(0.01));
        assertThat(display.path("usage_cell").asText()).isEqualTo("7.1 h");
    }

    @Test
    void build_usageCell_keepsDecimalHours_whenUsageAlreadyInHours() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.put("usage", 8.8);

        ObjectNode display = ComparisonTableDisplay.build(attrs, null);

        assertThat(display.path("usage_hours").asDouble()).isEqualTo(8.8);
        assertThat(display.path("usage_cell").asText()).isEqualTo("8.8 h");
    }

    @Test
    void build_apneaIndicesCell_hypopneaOnlyNight_matchesAhiTotal() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.putObject("ahi_summary").put("av", 0.19).put("oa", 0.0).put("ca", 0.0).put("h", 0.19);

        ObjectNode display = ComparisonTableDisplay.build(attrs, null);

        assertThat(display.path("osa_cell").asText()).isEqualTo("0/hr");
        assertThat(display.path("csa_cell").asText()).isEqualTo("0/hr");
        assertThat(display.path("h_cell").asText()).isEqualTo("0.19/hr");
        assertThat(display.path("apnea_indices_cell").asText())
                .isEqualTo("OSA 0/hr · CSA 0/hr · H 0.19/hr · AHI 0.19/hr");
    }

    @Test
    void build_flagsCsaElevated_whenCaAtThreshold() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        attrs.putObject("ahi_summary").put("av", 6.0).put("oa", 0.2).put("ca", 5.0);

        ObjectNode display = ComparisonTableDisplay.build(attrs, null);

        assertThat(display.path("csa_cell").asText()).isEqualTo("5/hr");
        assertThat(display.path("apnea_indices_cell").asText()).isEqualTo("OSA 0.2/hr · CSA 5/hr! · AHI 6/hr");
        assertThat(display.path("csa_elevated").asBoolean()).isTrue();
    }

    @Test
    void attachIfPresent_skipsWhenNightSkipped() {
        ObjectNode row = JsonApi.mapper().createObjectNode();
        row.put("skipped", true);

        ComparisonTableDisplay.attachIfPresent(row);

        assertThat(row.path("table_display").isMissingNode()).isTrue();
    }

    @Test
    void buildApneaIndicesCell_populatesFromAlternateFieldNames() {
        ObjectNode ahi = JsonApi.mapper().createObjectNode();
        ahi.put("total", 0.82);
        ahi.put("obstructive_apnea", 0.0);
        ahi.put("clear_airway", 0.69);
        ahi.put("hypopnea", 0.13);

        String cell = ComparisonTableDisplay.buildApneaIndicesCell(ahi);

        assertThat(cell).isNotBlank();
        assertThat(cell).contains("AHI");
        assertThat(cell).contains("CSA");
    }

    @Test
    void build_apneaIndicesCell_populatesFromStringTypedNumbers() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();
        ObjectNode ahi = attrs.putObject("ahi_summary");
        ahi.put("av", "0.45");
        ahi.put("oa", "0.0");
        ahi.put("ca", "0.12");
        ahi.put("h", "0.33");

        ObjectNode display = ComparisonTableDisplay.build(attrs, JsonApi.mapper().nullNode());

        assertThat(display.path("apnea_indices_cell").asText()).isNotBlank();
        assertThat(display.path("apnea_indices_cell").asText()).contains("AHI");
    }

    @Test
    void attachIfPresent_setsOsaElevatedAndCsaElevated_usingAlternateKeys() {
        ObjectNode row = JsonApi.mapper().createObjectNode();
        row.putObject("data").putObject("attributes")
                .putObject("ahi_summary")
                .put("total", 7.0)
                .put("obstructive_apnea", 2.5)
                .put("clear_airway", 6.0);

        ComparisonTableDisplay.attachIfPresent(row);

        assertThat(row.path("table_display").path("osa_elevated").asBoolean()).isTrue();
        assertThat(row.path("table_display").path("csa_elevated").asBoolean()).isTrue();
    }

    @Test
    void attachJournal_prefersMinutesByStageForReporting_overRawMinutesByStage() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();

        ObjectNode summary = JsonApi.mapper().createObjectNode();
        summary.put("asleep_minutes", 420.0);
        ObjectNode rawByStage = summary.putObject("minutes_by_stage");
        rawByStage.put("deep", 40.0);
        rawByStage.put("rem",  60.0);
        rawByStage.put("core", 200.0);
        rawByStage.put("awake", 20.0);
        ObjectNode resolvedByStage = summary.putObject("minutes_by_stage_for_reporting");
        resolvedByStage.put("deep", 55.0);
        resolvedByStage.put("rem",  75.0);
        resolvedByStage.put("core", 220.0);
        resolvedByStage.put("awake", 10.0);

        ObjectNode journal = JsonApi.mapper().createObjectNode();
        journal.set("sleep_stages_summary", summary);

        ObjectNode display = ComparisonTableDisplay.build(attrs, journal);

        assertThat(display.path("sleep_minutes").path("deep").asDouble()).isEqualTo(55.0);
        assertThat(display.path("sleep_minutes").path("rem").asDouble()).isEqualTo(75.0);
    }

    @Test
    void attachJournal_fallsBackToMinutesByStage_whenForReportingAbsent() {
        ObjectNode attrs = JsonApi.mapper().createObjectNode();

        ObjectNode summary = JsonApi.mapper().createObjectNode();
        summary.put("asleep_minutes", 360.0);
        ObjectNode rawByStage = summary.putObject("minutes_by_stage");
        rawByStage.put("deep", 45.0);
        rawByStage.put("rem", 65.0);
        rawByStage.put("core", 180.0);

        ObjectNode journal = JsonApi.mapper().createObjectNode();
        journal.set("sleep_stages_summary", summary);

        ObjectNode display = ComparisonTableDisplay.build(attrs, journal);

        assertThat(display.path("sleep_minutes").path("deep").asDouble()).isEqualTo(45.0);
    }
}
