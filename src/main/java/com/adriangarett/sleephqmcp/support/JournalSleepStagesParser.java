package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Attempts to parse {@code sleep_stages} when SleepHQ stores JSON inside the journal string field.
 */
public final class JournalSleepStagesParser {

    private JournalSleepStagesParser() {
    }

    /**
     * @return parsed JSON node, or {@code null} if value is blank or not valid JSON
     */
    public static JsonNode tryParse(String sleepStages) {
        if (sleepStages == null || sleepStages.isBlank()) {
            return null;
        }
        String trimmed = sleepStages.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            JsonNode node = JsonApi.parse(trimmed);
            if (node.isObject() || node.isArray()) {
                return node;
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
