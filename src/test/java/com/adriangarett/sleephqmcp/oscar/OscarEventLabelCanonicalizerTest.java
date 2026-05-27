package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OscarEventLabelCanonicalizerTest {

    // --- obstructive ---

    @Test
    void obstructiveApnea_mixedCase_returnsObstructive() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Obstructive Apnea")).isEqualTo("obstructive");
    }

    @Test
    void obstructiveApnea_allLower_returnsObstructive() {
        assertThat(OscarEventLabelCanonicalizer.canonical("obstructive apnea")).isEqualTo("obstructive");
    }

    // --- clear_airway (central / clear airway variants) ---

    @Test
    void centralApnea_returnsConformsToSummaryKey() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Central Apnea")).isEqualTo("clear_airway");
    }

    @Test
    void clearAirway_spaced_returnsClearAirway() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Clear Airway")).isEqualTo("clear_airway");
    }

    @Test
    void clearAirway_underscored_returnsClearAirway() {
        assertThat(OscarEventLabelCanonicalizer.canonical("clear_airway")).isEqualTo("clear_airway");
    }

    // --- hypopnea ---

    @Test
    void hypopnea_mixedCase_returnsHypopnea() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Hypopnea")).isEqualTo("hypopnea");
    }

    // --- flow_limit_events ---

    @Test
    void flowLimitation_returnsFlowLimitEvents() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Flow Limitation")).isEqualTo("flow_limit_events");
    }

    @Test
    void flowLimit_spaced_returnsFlowLimitEvents() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Flow Limit")).isEqualTo("flow_limit_events");
    }

    @Test
    void flowLimit_underscored_returnsFlowLimitEvents() {
        assertThat(OscarEventLabelCanonicalizer.canonical("flow_limit")).isEqualTo("flow_limit_events");
    }

    // --- large_leak ---

    @Test
    void largeLeak_returnsLargeLeak() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Large Leak")).isEqualTo("large_leak");
    }

    // --- rera ---

    @Test
    void rera_upperCase_returnsRera() {
        assertThat(OscarEventLabelCanonicalizer.canonical("RERA")).isEqualTo("rera");
    }

    @Test
    void arousal_returnsRera() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Arousal")).isEqualTo("rera");
    }

    // --- vibratory_snore ---

    @Test
    void vibratorySnore_returnsVibratorySnore() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Vibratory Snore")).isEqualTo("vibratory_snore");
    }

    @Test
    void snore_alone_returnsVibratorySnore() {
        assertThat(OscarEventLabelCanonicalizer.canonical("snore")).isEqualTo("vibratory_snore");
    }

    @Test
    void vibratory_alone_returnsVibratorySnore() {
        assertThat(OscarEventLabelCanonicalizer.canonical("vibratory")).isEqualTo("vibratory_snore");
    }

    // --- pressure_pulse ---

    @Test
    void pressurePulse_returnsPressurePulse() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Pressure Pulse")).isEqualTo("pressure_pulse");
    }

    // --- csr ---

    @Test
    void cheyneStokesCsr_returnsCsr() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Cheyne-Stokes")).isEqualTo("csr");
    }

    // --- bare apnea (after obstructive/central/hypopnea guards) ---

    @Test
    void apnea_bare_returnsApnea() {
        assertThat(OscarEventLabelCanonicalizer.canonical("apnea")).isEqualTo("apnea");
    }

    // --- null / non-therapy recording markers ---

    @ParameterizedTest
    @CsvSource({
        "Recording starts",
        "recording_end",
        "Recording Start",
        "recording_stop",
        "recording_start",
        "starting",
        "stopping",
        "start",
        "stop",
        "unknown"
    })
    void recordingMarkers_returnNull(String label) {
        assertThat(OscarEventLabelCanonicalizer.canonical(label)).isNull();
    }

    @Test
    void blank_returnsNull() {
        assertThat(OscarEventLabelCanonicalizer.canonical("")).isNull();
        assertThat(OscarEventLabelCanonicalizer.canonical("   ")).isNull();
    }

    @Test
    void null_returnsNull() {
        assertThat(OscarEventLabelCanonicalizer.canonical(null)).isNull();
    }

    // --- fallback snake-case ---

    @Test
    void unknownLabel_returnsFallbackSnakeCase() {
        assertThat(OscarEventLabelCanonicalizer.canonical("Unknown Label X")).isEqualTo("unknown_label_x");
    }

    // --- isNonTherapy ---

    @Test
    void isNonTherapy_recordingMarker_returnsTrue() {
        assertThat(OscarEventLabelCanonicalizer.isNonTherapy("recording_end")).isTrue();
    }

    @Test
    void isNonTherapy_obstructiveApnea_returnsFalse() {
        assertThat(OscarEventLabelCanonicalizer.isNonTherapy("Obstructive Apnea")).isFalse();
    }

    @Test
    void isNonTherapy_blank_returnsTrue() {
        assertThat(OscarEventLabelCanonicalizer.isNonTherapy("")).isTrue();
    }
}
