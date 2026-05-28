package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties;
import com.adriangarett.sleephqmcp.support.McpPayloadHints;
import com.adriangarett.sleephqmcp.support.ComparisonApneaTrendSupport;
import com.adriangarett.sleephqmcp.support.ComparisonTableDisplay;
import com.adriangarett.sleephqmcp.support.JournalOverlaySupport;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.adriangarett.sleephqmcp.support.VentilationSummarySupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Period "comparison" is computed locally: each calendar day uses documented
 * {@code GET /api/v1/machines/{id}/machine_dates/{date}} via {@link CombinedNightService} (CPAP + optional O2
 * overlay). There is no SleepHQ {@code /comparisons} API.
 */
@Service
public class ComparisonService {

    private static final int MAX_RANGE_DAYS = 120;

    private final CombinedNightService combinedNightService;
    private final JournalLookupService journalLookup;
    private final ClinicalContextProperties clinical;
    private final ExecutorService sleepHqFetchExecutor;
    private final SleepHqPayloadProperties payload;

    public ComparisonService(CombinedNightService combinedNightService, JournalLookupService journalLookup,
                             ClinicalContextProperties clinical, ExecutorService sleepHqFetchExecutor,
                             SleepHqPayloadProperties payload) {
        this.combinedNightService = combinedNightService;
        this.journalLookup = journalLookup;
        this.clinical = clinical;
        this.sleepHqFetchExecutor = sleepHqFetchExecutor;
        this.payload = payload;
    }

    /**
     * @param machineId CPAP (therapy) machine id
     * @return JSON with {@code meta} (range, machine ids, source) and {@code nights} array of per-day results
     */
    public String compare(String machineId, String fromDate, String toDate) {
        String cpap = SleepHqPathParams.requireResourceId(machineId, "machineId");
        String from = SleepHqPathParams.requireCalendarDate(fromDate, "fromDate");
        String to = SleepHqPathParams.requireCalendarDate(toDate, "toDate");
        LocalDate start = LocalDate.parse(from);
        LocalDate end = LocalDate.parse(to);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        long spanDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (spanDays > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range exceeds " + MAX_RANGE_DAYS + " days");
        }

        ObjectNode root = JsonApi.mapper().createObjectNode();
        ObjectNode meta = root.putObject("meta");
        meta.put("source", "sleephq-mcp/aggregated_range");
        meta.put("cpap_machine_id", cpap);
        if (clinical.defaultO2MachineId() != null && !clinical.defaultO2MachineId().isBlank()) {
            meta.put("o2_machine_id", clinical.defaultO2MachineId());
        }
        meta.put("from", from);
        meta.put("to", to);
        meta.put("note", "Each night is GET .../machines/{id}/machine_dates/{date}; O2 and journal wellness merged when configured. No upstream /comparisons.");
        meta.put("table_display_hint",
                "Each nights[] row: table_display (*_cell including apnea_indices_cell with ! if elevated). "
                        + "Root apnea_trends + titration_decision_support for physician_titration_review pressure decisions.");
        McpPayloadHints.attach(root, payload);

        Map<String, JsonNode> journalByDate = loadJournalMapSafely(start, end);

        List<LocalDate> days = start.datesUntil(end.plusDays(1)).toList();
        List<CompletableFuture<ObjectNode>> rowFutures = new ArrayList<>(days.size());
        for (LocalDate d : days) {
            String day = d.toString();
            Map<String, JsonNode> journalSnapshot = journalByDate;
            rowFutures.add(CompletableFuture.supplyAsync(
                    () -> buildNightRow(day, cpap, journalSnapshot),
                    sleepHqFetchExecutor));
        }
        CompletableFuture.allOf(rowFutures.toArray(CompletableFuture[]::new)).join();

        ArrayNode nights = root.putArray("nights");
        for (CompletableFuture<ObjectNode> future : rowFutures) {
            nights.add(future.join());
        }
        ComparisonTableDisplay.markSettingsChanges(nights);
        ComparisonApneaTrendSupport.attach(root, nights);
        attachTitrationReadiness(root);

        ObjectNode ventilation = VentilationSummarySupport.respiratoryRateFromSleepHq(nights);
        if (ventilation != null) {
            root.putObject("ventilation_summary").set("respiratory_rate_per_min", ventilation);
        }

        try {
            return JsonApi.mapper().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize comparison", e);
        }
    }

    private ObjectNode buildNightRow(String day, String cpap, Map<String, JsonNode> journalByDate) {
        ObjectNode row = JsonApi.mapper().createObjectNode();
        row.put("date", day);
        JsonNode journalAttrs = journalByDate.get(day);
        if (journalAttrs != null) {
            ObjectNode journalOut = JournalOverlaySupport.buildWellnessObject(journalAttrs);
            if (journalOut != null) {
                row.set("journal", journalOut);
            }
        }
        try {
            String envelope = combinedNightService.combineForCalendarDateWithJournalMap(day, cpap, null, journalByDate);
            JsonNode parsed = JsonApi.parse(envelope);
            row.set("data", parsed.path("data"));
            if (parsed.has("journal") && !row.has("journal")) {
                row.set("journal", parsed.get("journal").deepCopy());
            }
        } catch (RuntimeException e) {
            row.put("skipped", true);
            String msg = e.getMessage();
            row.put("reason", msg != null ? msg : e.getClass().getSimpleName());
        }
        ComparisonTableDisplay.attachIfPresent(row);
        return row;
    }

    private static void attachTitrationReadiness(ObjectNode root) {
        int withAhi = root.path("apnea_trends").path("nights_with_ahi_summary").asInt(0);
        int inSpan = root.path("apnea_trends").path("nights_in_span").asInt(0);
        ObjectNode readiness = root.putObject("titration_readiness");
        readiness.put("nights_in_span", inSpan);
        readiness.put("nights_with_ahi_summary", withAhi);
        readiness.put("ready_for_span_trends", withAhi > 0);
        if (withAhi == 0) {
            readiness.put("blocked_action",
                    "No nights had parseable ahi_summary values. Diagnostic steps: "
                    + "(1) Inspect nights[0].data.attributes.ahi_summary to see the actual keys the API returned — "
                    + "expected av/oa/ca/h or total/obstructive_apnea/clear_airway/hypopnea; "
                    + "(2) If all nights are skipped (skipped=true), check nights[].reason for a fetch error and verify SLEEPHQ_CPAP_MACHINE_ID; "
                    + "(3) If ahi_summary has data under different keys, compute trends manually from nights[].data.attributes. "
                    + "Do not halt the workflow — use raw per-night ahi_summary to derive span trends.");
        } else {
            readiness.put("blocked_action", "");
        }
    }

    private Map<String, JsonNode> loadJournalMapSafely(LocalDate start, LocalDate end) {
        try {
            return journalLookup.loadByDateRange(null, start, end);
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
    }
}
