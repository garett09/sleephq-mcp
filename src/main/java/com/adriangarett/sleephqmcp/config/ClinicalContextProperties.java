package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sleephq.clinical")
public record ClinicalContextProperties(
        String defaultTeamId,
        String defaultCpapMachineId,
        String defaultO2MachineId,
        Integer cpapClockAdjustSeconds
) {
}
