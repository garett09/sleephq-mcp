package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

/**
 * Loads the {@code attributes} JsonNode from SleepHQ {@code machine_date} for a given calendar date.
 * Returns {@code null} on any failure (404, 5xx, network error, missing config) so callers (e.g. trend)
 * are not blocked by a single night's unavailability.
 */
@Service
public class MachineDateAttributesLoader {

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;

    public MachineDateAttributesLoader(SleepHqClient client, ClinicalContextProperties clinical) {
        this.client = client;
        this.clinical = clinical;
    }

    /**
     * Returns the JsonAPI {@code attributes} node for the CPAP machine_date on {@code date},
     * or {@code null} on any failure / 404 / missing config.
     *
     * @param date YYYY-MM-DD therapy night
     */
    public JsonNode loadOrNull(String date) {
        String cpapMid = clinical.defaultCpapMachineId();
        if (cpapMid == null || cpapMid.isBlank()) {
            return null;
        }
        try {
            String raw = client.getMachineDateByDate(cpapMid, date);
            JsonNode doc = JsonApi.parse(raw);
            if (!JsonApi.hasSingleResourceData(doc)) {
                return null;
            }
            return JsonApi.singleResourceData(doc).path("attributes");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
