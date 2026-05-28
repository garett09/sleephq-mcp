package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.ApneaEvent;
import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpapClockAlignmentTest {

    private static final int DRIFT_SECONDS = 23 * 60 + 48;

    @Test
    void shiftIso8601_withAdjust_addsSeconds() {
        assertThat(CpapClockAlignment.shiftIso8601("2026-05-20T21:09:00", DRIFT_SECONDS))
                .isEqualTo("2026-05-20T21:32:48");
    }

    @Test
    void shiftIso8601_zeroAdjust_returnsOriginal() {
        assertThat(CpapClockAlignment.shiftIso8601("2026-05-20T21:09:00", 0))
                .isEqualTo("2026-05-20T21:09:00");
    }

    @Test
    void shiftIso8601_invalidIso_throws() {
        assertThatThrownBy(() -> CpapClockAlignment.shiftIso8601("not-a-datetime", 10))
                .isInstanceOf(Exception.class);
    }

    @Test
    void parseMachineDateTimeOffset_valid_returnsSeconds() {
        var attrs = JsonNodeFactory.instance.objectNode().put("time_offset", 1428);
        assertThat(CpapClockAlignment.parseMachineDateTimeOffset(attrs)).hasValue(1428);
    }

    @Test
    void parseMachineDateTimeOffset_tooLarge_empty() {
        var attrs = JsonNodeFactory.instance.objectNode().put("time_offset", 3605430137L);
        assertThat(CpapClockAlignment.parseMachineDateTimeOffset(attrs)).isEmpty();
    }

    @Test
    void parseMachineDateTimeOffset_negative_returnsValue() {
        var attrs = JsonNodeFactory.instance.objectNode().put("time_offset", -1428);
        assertThat(CpapClockAlignment.parseMachineDateTimeOffset(attrs)).hasValue(-1428);
    }

    @Test
    void resolveAdjust_negativeMachineDateOffset_applied() {
        var clinical = new ClinicalContextProperties("t", "c", "o", 100);
        var resolution = CpapClockAlignment.resolveAdjust(clinical, null, OptionalInt.of(-1428));
        assertThat(resolution.adjustSeconds()).isEqualTo(-1428);
        assertThat(resolution.source()).isEqualTo(CpapClockAlignment.SOURCE_SLEEPHQ_MACHINE_DATE);
    }

    @Test
    void resolveAdjust_toolOverrideWinsOverMachineDateAndEnv() {
        var clinical = new ClinicalContextProperties("t", "c", "o", 100);
        var resolution = CpapClockAlignment.resolveAdjust(clinical, DRIFT_SECONDS, OptionalInt.of(500));
        assertThat(resolution.adjustSeconds()).isEqualTo(DRIFT_SECONDS);
        assertThat(resolution.source()).isEqualTo(CpapClockAlignment.SOURCE_TOOL_OVERRIDE);
    }

    @Test
    void resolveAdjust_machineDateBeforeEnv() {
        var clinical = new ClinicalContextProperties("t", "c", "o", 100);
        var resolution = CpapClockAlignment.resolveAdjust(clinical, null, OptionalInt.of(DRIFT_SECONDS));
        assertThat(resolution.adjustSeconds()).isEqualTo(DRIFT_SECONDS);
        assertThat(resolution.source()).isEqualTo(CpapClockAlignment.SOURCE_SLEEPHQ_MACHINE_DATE);
    }

    @Test
    void resolveAdjust_envWhenNoMachineDate() {
        var clinical = new ClinicalContextProperties("t", "c", "o", DRIFT_SECONDS);
        var resolution = CpapClockAlignment.resolveAdjust(clinical, null, OptionalInt.empty());
        assertThat(resolution.adjustSeconds()).isEqualTo(DRIFT_SECONDS);
        assertThat(resolution.source()).isEqualTo(CpapClockAlignment.SOURCE_ENV);
    }

    @Test
    void resolveAdjust_negativeOverride_throwsWhenOutOfBounds() {
        assertThatThrownBy(() -> CpapClockAlignment.resolveAdjust(null, -100_000, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute value must not exceed");
    }

    @Test
    void alignApneaScan_shiftsStartAndEventTimestamps_notOffset() {
        ApneaScanResult raw = new ApneaScanResult(
                "brp.edf",
                "2026-05-20T21:09:00",
                3600,
                "Flow",
                0.08,
                List.of(new ApneaEvent("00:00:06", 6.84, 18.28, "2026-05-20T21:09:06.840", "APNEA_OBSTRUCTIVE"))
        );

        ApneaScanResult aligned = CpapClockAlignment.alignApneaScan(raw, DRIFT_SECONDS);
        assertThat(aligned.startDatetime()).isEqualTo("2026-05-20T21:32:48");
        assertThat(aligned.events().get(0).offset()).isEqualTo("00:00:06");
        assertThat(aligned.events().get(0).timestamp()).isEqualTo("2026-05-20T21:32:54.840");
    }

    @Test
    void alignDeviceEvents_shiftsTimestamps() {
        DeviceEventResult raw = new DeviceEventResult(
                "eve.edf",
                "2026-05-12T23:00:00",
                100,
                "device_eve",
                List.of(new DeviceEvent("00:00:10", 10, 5, "2026-05-12T23:00:10", "Hypopnea", "H"))
        );

        DeviceEventResult aligned = CpapClockAlignment.alignDeviceEvents(raw, DRIFT_SECONDS);
        assertThat(aligned.startDatetime()).isEqualTo("2026-05-12T23:23:48");
        assertThat(aligned.events().get(0).timestamp()).isEqualTo("2026-05-12T23:23:58");
        assertThat(aligned.events().get(0).offset()).isEqualTo("00:00:10");
    }

    @Test
    void serializeWithAlignment_includesMetadataWhenAdjustPositive() {
        DeviceEventResult aligned = new DeviceEventResult("f", "2026-05-12T23:23:48", 10, "device_eve", List.of());
        var resolution = new CpapClockAlignment.CpapClockAdjustResolution(
                DRIFT_SECONDS, CpapClockAlignment.SOURCE_SLEEPHQ_MACHINE_DATE);
        String json = CpapClockAlignment.serializeWithAlignment(aligned, resolution);
        assertThat(json).contains("\"clock_alignment\"");
        assertThat(json).contains("\"cpap_adjust_seconds\":1428");
        assertThat(json).contains("\"source\":\"sleephq_machine_date\"");
    }
}
