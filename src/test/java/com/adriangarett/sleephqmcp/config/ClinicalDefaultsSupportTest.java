package com.adriangarett.sleephqmcp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalDefaultsSupportTest {

    @Test
    void misconfigurationWarning_whenCpapEqualsTeam_returnsWarning() {
        var clinical = new ClinicalContextProperties("32809", "32809", "81007", null);

        assertThat(ClinicalDefaultsSupport.misconfigurationWarning(clinical))
                .isPresent()
                .get()
                .asString()
                .contains("SLEEPHQ_CPAP_MACHINE_ID");
    }

    @Test
    void misconfigurationWarning_whenCpapDiffersFromTeam_empty() {
        var clinical = new ClinicalContextProperties("32809", "81272", "81007", null);

        assertThat(ClinicalDefaultsSupport.misconfigurationWarning(clinical)).isEmpty();
    }

    @Test
    void configuredDefaultsBody_includesWarningWhenMisconfigured() {
        var clinical = new ClinicalContextProperties("32809", "32809", "81007", null);

        assertThat(ClinicalDefaultsSupport.configuredDefaultsBody(clinical))
                .containsEntry("team_id", "32809")
                .containsEntry("cpap_machine_id", "32809")
                .containsKey("warning");
    }

    @Test
    void configuredDefaultsBody_includesCpapClockAdjustWhenSet() {
        var clinical = new ClinicalContextProperties("32809", "81272", "81007", 1428);

        assertThat(ClinicalDefaultsSupport.configuredDefaultsBody(clinical))
                .containsEntry("cpap_clock_adjust_seconds_env_fallback", 1428)
                .containsEntry("clock_reference", "o2_and_journal");
    }
}
