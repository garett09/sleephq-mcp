package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds a top-level {@code journal} sibling on night envelopes without altering JSON:API {@code data}.
 */
public final class JournalOverlaySupport {

    private static final String[] WELLNESS_KEYS = {
            "date", "step_count", "sleep_stages", "active_energy_joules",
            "feeling_score", "weight_grams", "notes"
    };

    private JournalOverlaySupport() {
    }

    /**
     * @param envelope JSON object node (e.g. {@code { "data": ... }})
     * @param journalAttributes journal {@code attributes} from list/get journal; omitted when null/empty
     */
    public static void attachIfPresent(ObjectNode envelope, JsonNode journalAttributes) {
        ObjectNode wellness = buildWellnessObject(journalAttributes);
        if (wellness != null && !wellness.isEmpty()) {
            envelope.set("journal", wellness);
        }
    }

    /**
     * @return wellness object for MCP consumers, or {@code null} when no usable attributes
     */
    public static ObjectNode buildWellnessObject(JsonNode journalAttributes) {
        if (journalAttributes == null || journalAttributes.isMissingNode() || !journalAttributes.isObject()) {
            return null;
        }
        ObjectNode out = JsonApi.mapper().createObjectNode();
        for (String key : WELLNESS_KEYS) {
            if ("sleep_stages".equals(key)) {
                continue;
            }
            JsonNode v = journalAttributes.get(key);
            if (v != null && !v.isNull() && !v.isMissingNode()) {
                out.set(key, v.deepCopy());
            }
        }
        if (out.path("feeling_score").isNumber()) {
            int score = out.path("feeling_score").asInt();
            JournalFeelingScore.labelFor(score).ifPresent(label -> out.put("feeling_label", label));
        }
        JsonNode stages = journalAttributes.path("sleep_stages");
        if (stages.isTextual()) {
            JsonNode parsed = JournalSleepStagesParser.tryParse(stages.asText());
            if (parsed != null) {
                out.set("sleep_stages_parsed", parsed);
                ObjectNode summary = JournalSleepStagesSummary.summarize(parsed);
                if (summary != null) {
                    out.set("sleep_stages_summary", summary);
                } else {
                    out.put("sleep_stages", stages.asText());
                }
            } else {
                out.put("sleep_stages", stages.asText());
            }
        }
        return out.isEmpty() ? null : out;
    }

    public static String enrichEnvelopeJson(String envelopeJson, JsonNode journalAttributes) {
        ObjectNode envelope = (ObjectNode) JsonApi.parse(envelopeJson);
        attachIfPresent(envelope, journalAttributes);
        try {
            return JsonApi.mapper().writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize enriched night envelope", e);
        }
    }

    /**
     * Reads {@code data.attributes.date} or {@code data.date} from a machine_date envelope.
     */
    public static String resolveCalendarDate(JsonNode envelope) {
        JsonNode data = envelope.path("data");
        if (!data.isObject()) {
            return null;
        }
        String fromAttrs = data.path("attributes").path("date").asText(null);
        if (fromAttrs != null && !fromAttrs.isBlank()) {
            return fromAttrs;
        }
        return data.path("date").asText(null);
    }
}
