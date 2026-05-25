package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.CpapClockAlignment;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.OptionalInt;

/**
 * Loads {@code time_offset} from SleepHQ {@code machine_date} for CPAP clock drift on EDF tools.
 */
@Service
public class MachineDateTimeOffsetLoader {

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;

    public MachineDateTimeOffsetLoader(SleepHqClient client, ClinicalContextProperties clinical) {
        this.client = client;
        this.clinical = clinical;
    }

    /**
     * @param calendarDate YYYY-MM-DD therapy night
     * @param cpapMachineIdOverride optional; else {@code SLEEPHQ_CPAP_MACHINE_ID}
     * @return seconds to add to CPAP EDF wall times when SleepHQ provides a valid offset
     */
    public OptionalInt loadForCpapDate(String calendarDate, String cpapMachineIdOverride) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        String cpapMid = resolveCpapMachineId(cpapMachineIdOverride);
        if (cpapMid == null) {
            return OptionalInt.empty();
        }
        try {
            String raw = client.getMachineDateByDate(cpapMid, date);
            JsonNode doc = JsonApi.parse(raw);
            if (!JsonApi.hasSingleResourceData(doc)) {
                return OptionalInt.empty();
            }
            JsonNode attrs = JsonApi.singleResourceData(doc).path("attributes");
            return CpapClockAlignment.parseMachineDateTimeOffset(attrs);
        } catch (RuntimeException e) {
            return OptionalInt.empty();
        }
    }

    private String resolveCpapMachineId(String override) {
        if (override != null && !override.isBlank()) {
            return SleepHqPathParams.requireResourceId(override, "cpapMachineId");
        }
        if (clinical.defaultCpapMachineId() == null || clinical.defaultCpapMachineId().isBlank()) {
            return null;
        }
        return SleepHqPathParams.requireResourceId(clinical.defaultCpapMachineId(), "SLEEPHQ_CPAP_MACHINE_ID");
    }
}
