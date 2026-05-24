package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Builds one JSON:API {@code machine_date} document for a calendar night: CPAP is the primary
 * {@code data} resource (same shape as {@code GET /api/v1/machine_dates/{id}}); when the CPAP row
 * lacks oximetry summaries, copies {@code spo2_summary}, {@code pulse_rate_summary}, and
 * {@code movement_summary} from the O2 Ring machine's {@code machine_date} for that date when configured
 * ({@code SLEEPHQ_O2_MACHINE_ID} or tool override); if no O2 machine is configured, returns CPAP-only.
 */
@Service
public class CombinedNightService {

    private static final List<String> O2_OVERLAY_ATTRIBUTE_KEYS = List.of(
            "spo2_summary", "pulse_rate_summary", "movement_summary");

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;

    public CombinedNightService(SleepHqClient client, ClinicalContextProperties clinical) {
        this.client = client;
        this.clinical = clinical;
    }

    /**
     * @param calendarDate YYYY-MM-DD
     * @param cpapMachineId optional override; otherwise {@code SLEEPHQ_CPAP_MACHINE_ID}
     * @param o2MachineId optional override; otherwise {@code SLEEPHQ_O2_MACHINE_ID}
     * @return JSON string {@code { "data": { "id", "type": "machine_date", "attributes", "relationships" } }}
     */
    public String combineForCalendarDate(String calendarDate, String cpapMachineId, String o2MachineId) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        String cpapMid = resolveMachineId(cpapMachineId, clinical.defaultCpapMachineId(), "cpapMachineId",
                "SLEEPHQ_CPAP_MACHINE_ID");
        String o2Mid = resolveO2MachineIdOptional(o2MachineId);

        JsonNode cpapDoc = requireCpapDocument(() -> client.getMachineDateByDate(cpapMid, date), date);
        JsonNode cpapData = cpapDoc.path("data");
        if (!cpapData.path("attributes").isObject()) {
            throw new IllegalStateException("CPAP machine_date response missing data.attributes");
        }
        JsonNode o2Attrs = o2Mid == null ? null : loadO2AttributesOrNull(o2Mid, date);

        ObjectNode mergedAttrs = mergeAttributes((ObjectNode) cpapData.path("attributes").deepCopy(), o2Attrs);

        ObjectNode dataOut = JsonApi.mapper().createObjectNode();
        dataOut.set("id", cpapData.get("id"));
        dataOut.put("type", "machine_date");
        dataOut.set("attributes", mergedAttrs);
        JsonNode rel = cpapData.path("relationships");
        if (rel.isObject()) {
            dataOut.set("relationships", rel.deepCopy());
        } else {
            dataOut.set("relationships", JsonApi.mapper().createObjectNode());
        }

        ObjectNode envelope = JsonApi.mapper().createObjectNode();
        envelope.set("data", dataOut);
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

    private static JsonNode requireCpapDocument(java.util.function.Supplier<String> fetch, String date) {
        try {
            return JsonApi.parse(fetch.get());
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "No CPAP machine_date for date=" + date + " (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (Exception e) {
            throw new IllegalStateException("CPAP machine_date fetch failed for date=" + date, e);
        }
    }

    private JsonNode loadO2AttributesOrNull(String o2MachineId, String date) {
        try {
            JsonNode doc = JsonApi.parse(client.getMachineDateByDate(o2MachineId, date));
            return doc.path("data").path("attributes");
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new IllegalStateException(
                    "O2 machine_date fetch failed (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (Exception e) {
            throw new IllegalStateException("O2 machine_date fetch failed", e);
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
