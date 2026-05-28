package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.CpapBrpSessionWindow;
import com.adriangarett.sleephqmcp.support.JournalOverlaySupport;
import com.adriangarett.sleephqmcp.support.NightTherapyDisplaySupport;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds one JSON:API {@code machine_date} document for a calendar night. CPAP is preferred as
 * {@code data}; when CPAP is absent (e.g. no Magic Uploader yet), uses the O2 machine's
 * {@code machine_date} as {@code data} when present; journal wellness is attached when
 * {@code SLEEPHQ_TEAM_ID} is set. When CPAP exists but lacks oximetry summaries, copies
 * {@code spo2_summary}, {@code pulse_rate_summary}, and {@code movement_summary} from O2.
 * Throws only when no CPAP, O2, or journal row exists for the date.
 */
@Service
public class CombinedNightService {

    private static final List<String> O2_OVERLAY_ATTRIBUTE_KEYS = List.of(
            "spo2_summary", "pulse_rate_summary", "movement_summary");

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;
    private final JournalLookupService journalLookup;
    private final UnifiedNightAnalysisService nightAnalysisService;
    private final WaveformService waveformService;

    public CombinedNightService(SleepHqClient client, ClinicalContextProperties clinical,
                                JournalLookupService journalLookup,
                                UnifiedNightAnalysisService nightAnalysisService,
                                WaveformService waveformService) {
        this.client = client;
        this.clinical = clinical;
        this.journalLookup = journalLookup;
        this.nightAnalysisService = nightAnalysisService;
        this.waveformService = waveformService;
    }

    /**
     * @param calendarDate YYYY-MM-DD
     * @param cpapMachineId optional override; otherwise {@code SLEEPHQ_CPAP_MACHINE_ID}
     * @param o2MachineId optional override; otherwise {@code SLEEPHQ_O2_MACHINE_ID}
     * @return JSON string {@code { "data": { "id", "type": "machine_date", "attributes", "relationships" } }}
     */
    public String combineForCalendarDate(String calendarDate, String cpapMachineId, String o2MachineId) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        ObjectNode envelope = buildMachineDateEnvelope(date, cpapMachineId, o2MachineId);
        attachJournalSafely(envelope, date);
        finalizeEnvelopeOrThrow(envelope, date, cpapMachineId, o2MachineId);
        attachNightAnalysis(envelope, date);
        return serializeEnvelope(envelope);
    }

    /**
     * Same machine_date merge as {@link #combineForCalendarDate} but attaches journal from a preloaded map
     * (avoids repeated {@code list-journals} during {@code get-comparison}).
     */
    public String combineForCalendarDateWithJournalMap(String calendarDate, String cpapMachineId, String o2MachineId,
                                                       Map<String, JsonNode> journalByDate) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        ObjectNode envelope = buildMachineDateEnvelope(date, cpapMachineId, o2MachineId);
        if (journalByDate != null) {
            JsonNode attrs = journalByDate.get(date);
            if (attrs != null) {
                attachJournal(envelope, attrs, date, cpapMachineId, o2MachineId);
            }
        }
        attachCoverage(envelope);
        NightTherapyDisplaySupport.attachIfPresent(envelope);
        attachNightAnalysis(envelope, date);
        return serializeEnvelope(envelope);
    }

    private void attachNightAnalysis(ObjectNode envelope, String date) {
        JsonNode attrs = envelope.path("data").path("attributes");
        JsonNode journal = envelope.has("journal") ? envelope.get("journal") : null;
        JsonNode machineAttrs = attrs.isObject() ? attrs : null;
        nightAnalysisService.analyzeNight(date, machineAttrs, journal).ifPresent(analysis -> {
            envelope.set("night_analysis", analysis);
            envelope.put("oscar_status", "ok");
            ArrayNode dataSources = envelope.putArray("data_sources");
            if (envelope.path("data").isObject()) {
                dataSources.add("sleephq");
            }
            if (analysis.has("data_sources") && analysis.get("data_sources").isArray()) {
                analysis.get("data_sources").forEach(source -> {
                    if (!source.asText().equals("sleephq")) {
                        dataSources.add(source.asText());
                    }
                });
            }
        });
        if (!envelope.has("oscar_status")) {
            envelope.put("oscar_status", "unavailable");
        }
    }

    private ObjectNode buildMachineDateEnvelope(String date, String cpapMachineId, String o2MachineId) {
        String cpapMid = resolveMachineId(cpapMachineId, clinical.defaultCpapMachineId(), "cpapMachineId",
                "SLEEPHQ_CPAP_MACHINE_ID");
        String o2Mid = resolveO2MachineIdOptional(o2MachineId);

        JsonNode cpapData = loadMachineDateResourceOrNull(cpapMid, date, "CPAP");
        JsonNode o2Data = o2Mid == null ? null : loadMachineDateResourceOrNull(o2Mid, date, "O2");
        JsonNode o2Attrs = o2Data == null ? null : o2Data.path("attributes");

        ObjectNode envelope = JsonApi.mapper().createObjectNode();
        if (cpapData != null) {
            ObjectNode mergedAttrs = mergeAttributes((ObjectNode) cpapData.path("attributes").deepCopy(), o2Attrs);
            envelope.set("data", buildDataResource(cpapData, mergedAttrs));
        } else if (o2Data != null) {
            ObjectNode o2AttrsCopy = (ObjectNode) o2Data.path("attributes").deepCopy();
            envelope.set("data", buildDataResource(o2Data, o2AttrsCopy));
        } else {
            envelope.putNull("data");
        }
        attachCoverage(envelope, cpapData != null, o2Data != null);
        return envelope;
    }

    private void attachJournalSafely(ObjectNode envelope, String date) {
        try {
            journalLookup.findAttributesByDate(null, date)
                    .ifPresent(attrs -> attachJournal(envelope, attrs, date, null, null));
        } catch (IllegalArgumentException e) {
            // SLEEPHQ_TEAM_ID not configured — journal overlay skipped
        }
        attachCoverage(envelope);
    }

    private void attachJournal(ObjectNode envelope,
                               JsonNode journalAttrs,
                               String date,
                               String cpapMachineId,
                               String o2MachineId) {
        Optional<CpapBrpSessionWindow.Bounds> bounds =
                CpapBrpSessionWindow.tryResolve(waveformService, null, date, null);
        Instant clipStart = bounds.map(CpapBrpSessionWindow.Bounds::start).orElse(null);
        Instant clipEnd = bounds.map(CpapBrpSessionWindow.Bounds::end).orElse(null);
        ObjectNode wellness = JournalOverlaySupport.buildWellnessObject(journalAttrs, clipStart, clipEnd);
        if (wellness != null && !wellness.isEmpty()) {
            envelope.set("journal", wellness);
        }
    }

    private void finalizeEnvelopeOrThrow(ObjectNode envelope, String date, String cpapMachineId, String o2MachineId) {
        attachCoverage(envelope);
        if (envelope.path("data").isObject() || envelope.has("journal")) {
            NightTherapyDisplaySupport.attachIfPresent(envelope);
            return;
        }
        String cpapMid = resolveMachineId(cpapMachineId, clinical.defaultCpapMachineId(), "cpapMachineId",
                "SLEEPHQ_CPAP_MACHINE_ID");
        String o2Mid = resolveO2MachineIdOptional(o2MachineId);
        throw new IllegalStateException(buildNoNightDataMessage(date, cpapMid, o2Mid));
    }

    private static String buildNoNightDataMessage(String date, String cpapMid, String o2Mid) {
        StringBuilder msg = new StringBuilder("No night data for date=").append(date);
        msg.append(" (no CPAP machine_date on machine_id=").append(cpapMid);
        if (o2Mid != null) {
            msg.append("; no O2 machine_date on machine_id=").append(o2Mid);
        }
        msg.append("; no team journal row). Without Magic Uploader, CPAP nights are absent — use ");
        msg.append("get-journal-by-date for sleep stages/steps, get-o2-oximetry for ring curves, ");
        msg.append("or list-machine-dates when therapy uploads resume.");
        return msg.toString();
    }

    private static ObjectNode buildDataResource(JsonNode source, ObjectNode attributes) {
        ObjectNode dataOut = JsonApi.mapper().createObjectNode();
        dataOut.set("id", source.get("id"));
        dataOut.put("type", source.path("type").asText("machine_date"));
        dataOut.set("attributes", attributes);
        JsonNode rel = source.path("relationships");
        if (rel.isObject()) {
            dataOut.set("relationships", rel.deepCopy());
        } else {
            dataOut.set("relationships", JsonApi.mapper().createObjectNode());
        }
        return dataOut;
    }

    private static void attachCoverage(ObjectNode envelope) {
        boolean cpap = envelope.path("coverage").path("cpap_machine_date").asBoolean(false);
        boolean o2 = envelope.path("coverage").path("o2_machine_date").asBoolean(false);
        attachCoverage(envelope, cpap, o2);
    }

    private static void attachCoverage(ObjectNode envelope, boolean cpapMachineDate, boolean o2MachineDate) {
        ObjectNode coverage = envelope.putObject("coverage");
        coverage.put("cpap_machine_date", cpapMachineDate);
        coverage.put("o2_machine_date", o2MachineDate);
        coverage.put("journal", envelope.has("journal"));
        if (cpapMachineDate) {
            coverage.put("primary_source", "cpap");
        } else if (o2MachineDate) {
            coverage.put("primary_source", "o2");
        } else {
            coverage.put("primary_source", "none");
        }
    }

    private static String serializeEnvelope(ObjectNode envelope) {
        try {
            return JsonApi.mapper().writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize combined night", e);
        }
    }

    private static String resolveMachineId(String override, String configured, String paramName, String envKey) {
        if (override != null && !override.isBlank()) {
            return SleepHqPathParams.requireResourceId(override, paramName);
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException(paramName + " is required, or set " + envKey + " for a default");
        }
        return SleepHqPathParams.requireResourceId(configured, envKey);
    }

    /**
     * O2 machine id from tool override, else env; {@code null} if neither is set (CPAP-only merge).
     */
    private String resolveO2MachineIdOptional(String o2MachineId) {
        if (o2MachineId != null && !o2MachineId.isBlank()) {
            return SleepHqPathParams.requireResourceId(o2MachineId, "o2MachineId");
        }
        if (clinical.defaultO2MachineId() == null || clinical.defaultO2MachineId().isBlank()) {
            return null;
        }
        return SleepHqPathParams.requireResourceId(clinical.defaultO2MachineId(), "SLEEPHQ_O2_MACHINE_ID");
    }

    private JsonNode loadMachineDateResourceOrNull(String machineId, String date, String label) {
        final String raw;
        try {
            raw = client.getMachineDateByDate(machineId, date);
        } catch (RestClientException e) {
            if (e instanceof RestClientResponseException rre && rre.getStatusCode().value() == 404) {
                return null;
            }
            String suffix = "";
            if (e instanceof RestClientResponseException rre) {
                suffix = " (HTTP " + rre.getStatusCode().value() + ")";
            }
            throw new IllegalStateException(label + " machine_date fetch failed for date=" + date + suffix, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    label + " machine_date fetch failed for date=" + date + ": " + e.getMessage(), e);
        }
        try {
            JsonNode doc = JsonApi.parse(raw);
            if (!JsonApi.hasSingleResourceData(doc)) {
                return null;
            }
            return JsonApi.singleResourceData(doc);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    label + " machine_date response invalid for date=" + date + ": " + e.getMessage(), e);
        }
    }

    static ObjectNode mergeAttributes(ObjectNode cpapAttrs, JsonNode o2Attrs) {
        if (o2Attrs == null || o2Attrs.isMissingNode() || !o2Attrs.isObject()) {
            return cpapAttrs;
        }
        ObjectNode out = cpapAttrs;
        for (String key : O2_OVERLAY_ATTRIBUTE_KEYS) {
            JsonNode o2v = o2Attrs.get(key);
            if (o2v == null || o2v.isNull()) {
                continue;
            }
            if (o2v.isObject() && o2v.isEmpty()) {
                continue;
            }
            if (isEffectivelyEmpty(out.get(key))) {
                out.set(key, o2v.deepCopy());
            }
        }
        return out;
    }

    private static boolean isEffectivelyEmpty(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return true;
        }
        return n.isObject() && n.isEmpty();
    }
}
