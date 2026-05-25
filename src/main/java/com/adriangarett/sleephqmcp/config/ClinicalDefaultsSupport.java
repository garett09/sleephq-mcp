package com.adriangarett.sleephqmcp.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
        misconfigurationWarning(clinical).ifPresent(w -> body.put("warning", w));
        return body;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
