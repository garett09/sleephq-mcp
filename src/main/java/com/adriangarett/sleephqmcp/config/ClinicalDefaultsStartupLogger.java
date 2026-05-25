package com.adriangarett.sleephqmcp.config;

import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ClinicalDefaultsStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDefaultsStartupLogger.class);

    private final ClinicalContextProperties clinical;

    public ClinicalDefaultsStartupLogger(ClinicalContextProperties clinical) {
        this.clinical = clinical;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConfiguredDefaults() {
        log.info(
                "SleepHQ defaults loaded: team_id={}, cpap_machine_id={}, o2_machine_id={}",
                Encode.forJava(String.valueOf(clinical.defaultTeamId())),
                Encode.forJava(String.valueOf(clinical.defaultCpapMachineId())),
                Encode.forJava(String.valueOf(clinical.defaultO2MachineId())));
        ClinicalDefaultsSupport.misconfigurationWarning(clinical).ifPresent(w -> log.warn(Encode.forJava(w)));
    }
}
