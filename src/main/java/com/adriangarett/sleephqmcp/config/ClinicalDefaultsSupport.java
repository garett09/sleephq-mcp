package com.adriangarett.sleephqmcp.config;

import com.adriangarett.sleephqmcp.support.CpapClockAlignment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Validates and surfaces configured SleepHQ team/machine defaults (non-secret).
 */
public final class ClinicalDefaultsSupport {

    private ClinicalDefaultsSupport() {
    }

    public static Optional<String> misconfigurationWarning(ClinicalContextProperties clinical) {
        if (clinical == null) {
            return Optional.empty();
        }
        String team = clinical.defaultTeamId();
        String cpap = clinical.defaultCpapMachineId();
        if (team != null && !team.isBlank() && team.equals(cpap)) {
            return Optional.of(
                    "SLEEPHQ_CPAP_MACHINE_ID equals SLEEPHQ_TEAM_ID; use the CPAP machine id from list-machines "
                            + "(e.g. AirSense), not the team id");
        }
        return Optional.empty();
    }

    public static Map<String, Object> configuredDefaultsBody(ClinicalContextProperties clinical) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("team_id", blankToNull(clinical.defaultTeamId()));
        body.put("cpap_machine_id", blankToNull(clinical.defaultCpapMachineId()));
        body.put("o2_machine_id", blankToNull(clinical.defaultO2MachineId()));
        int cpapAdjustEnv = CpapClockAlignment.resolveAdjust(clinical, null, OptionalInt.empty()).adjustSeconds();
        if (cpapAdjustEnv > 0) {
            body.put("cpap_clock_adjust_seconds_env_fallback", cpapAdjustEnv);
            body.put("cpap_clock_source_note", "EDF tools prefer machine_date.time_offset when date is provided");
            body.put("clock_reference", CpapClockAlignment.REFERENCE_CLOCK);
        }
        misconfigurationWarning(clinical).ifPresent(w -> body.put("warning", w));
        return body;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
