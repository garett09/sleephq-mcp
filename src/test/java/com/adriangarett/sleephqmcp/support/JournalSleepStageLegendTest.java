package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JournalSleepStageLegendTest {

    @Test
    void detect_prefersSleephqDashboard_whenHkMappingImplausible() {
        Map<String, Long> hk = Map.of(
                "awake", 217L * 60,
                "rem", 13L * 60,
                "deep", 100L * 60,
                "core", 300L * 60);
        Map<String, Long> shq = Map.of(
                "awake", 14L * 60,
                "rem", 240L * 60,
                "deep", 217L * 60,
                "core", 358L * 60);

        assertThat(JournalSleepStageLegend.detect(hk, shq)).isEqualTo(JournalSleepStageLegend.SLEEPHQ_DASHBOARD);
    }

    @Test
    void detect_prefersSleephqDashboard_whenHkRemModerateButAwakeStillHeavy() {
        Map<String, Long> hk = Map.of(
                "awake", 209L * 60,
                "rem", 73L * 60,
                "deep", 191L * 60,
                "core", 468L * 60);
        Map<String, Long> shq = Map.of(
                "awake", 73L * 60,
                "rem", 191L * 60,
                "deep", 209L * 60,
                "core", 468L * 60);

        assertThat(JournalSleepStageLegend.detect(hk, shq)).isEqualTo(JournalSleepStageLegend.SLEEPHQ_DASHBOARD);
    }

    @Test
    void detect_keepsAppleHealth_forTypicalSegments() {
        Map<String, Long> hk = new LinkedHashMap<>();
        hk.put("awake", 20L * 60);
        hk.put("rem", 30L * 60);
        hk.put("deep", 81L * 60);
        hk.put("core", 22L * 60);
        assertThat(JournalSleepStageLegend.detect(hk, hk)).isEqualTo(JournalSleepStageLegend.APPLE_HEALTH);
    }
}
