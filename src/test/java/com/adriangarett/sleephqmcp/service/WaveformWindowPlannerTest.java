package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.ApneaEvent;
import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.domain.WaveformWindowPlan;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.WaveformAnchorSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaveformWindowPlannerTest {

    @Mock
    private DeviceEventService deviceEventService;

    @Mock
    private WaveformService waveformService;

    @Mock
    private UnifiedNightAnalysisService nightAnalysisService;

    @Mock
    private JournalLookupService journalLookupService;

    private WaveformWindowPlanner planner;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-1", "cpap-1", "o2-1", null);
        planner = new WaveformWindowPlanner(
                deviceEventService, waveformService, nightAnalysisService, clinical, journalLookupService);
    }

    @Test
    void plan_manualStartMinute_returnsManualAnchor() {
        WaveformWindowPlan plan = planner.plan(
                "team-1", "2026-05-19", "auto", 1, 0, 145, 15, null);

        assertThat(plan.anchorResolved()).isEqualTo(WaveformAnchorSupport.ANCHOR_MANUAL);
        assertThat(plan.startMinute()).isEqualTo(145);
        assertThat(plan.startSeconds()).isEqualTo(145 * 60);
        assertThat(plan.evidence()).isEmpty();
    }

    @Test
    void plan_unknownAnchor_throwsInvalidAnchor() {
        assertThatThrownBy(() -> planner.plan(
                "team-1", "2026-05-19", "bogus_anchor", 1, 0, null, 15, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid_anchor");
    }

    @Test
    void plan_autoNoCandidates_throwsNoAnchorCandidates() {
        when(waveformService.resolveBrpFileId(eq("team-1"), eq("2026-05-19"))).thenReturn("brp-file");
        when(deviceEventService.loadDeviceEventsByDate(eq("team-1"), eq("2026-05-19"), isNull()))
                .thenReturn(emptyEve());
        when(waveformService.loadScanByDate(eq("team-1"), eq("2026-05-19"), isNull()))
                .thenReturn(emptyScan());
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planner.plan(
                "team-1", "2026-05-19", "auto", 1, 0, null, 15, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no_anchor_candidates");
    }

    @Test
    void plan_eveScanOverlap_picksHypopneaClusterWithLeadIn() {
        when(waveformService.resolveBrpFileId(eq("team-1"), eq("2026-05-19"))).thenReturn("brp-file");

        DeviceEvent eveHyp = new DeviceEvent("01:37:00", 5820.0, 12.0, "2026-05-19T04:37:00", "Hypopnea", "H");
        DeviceEventResult eve = new DeviceEventResult("EVE.edf", "2026-05-19T00:00:00", 28800, "eve",
                List.of(eveHyp));
        when(deviceEventService.loadDeviceEventsByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(eve);

        ApneaEvent scanHyp = new ApneaEvent("01:36:57", 5817.0, 14.0, "2026-05-19T04:36:57", "HYPOPNEA");
        ApneaScanResult scan = new ApneaScanResult("BRP.edf", "2026-05-19T00:00:00", 28800, "Flow", 0.15,
                List.of(scanHyp));
        when(waveformService.loadScanByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(scan);
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.empty());

        WaveformWindowPlan plan = planner.plan(
                "team-1", "2026-05-19", WaveformAnchorSupport.ANCHOR_EVE_SCAN_OVERLAP, 1, 0, null, 15, null);

        assertThat(plan.anchorResolved()).isEqualTo(WaveformAnchorSupport.ANCHOR_EVE_SCAN_OVERLAP);
        assertThat(plan.startMinute()).isEqualTo(92);
        assertThat(plan.startSeconds()).isEqualTo(5520);
        assertThat(plan.evidence()).hasSize(2);
    }

    @Test
    void plan_worstObstructive_ignoresRecordingStart() {
        when(waveformService.resolveBrpFileId(eq("team-1"), eq("2026-05-19"))).thenReturn("brp-file");

        DeviceEvent recording = new DeviceEvent("00:00:00", 0.0, 0.0, "2026-05-19T00:00:00", "Recording starts", "RS");
        DeviceEvent hyp = new DeviceEvent("04:31:00", 16260.0, 10.0, "2026-05-19T04:31:00", "Hypopnea", "H");
        DeviceEventResult eve = new DeviceEventResult("EVE.edf", "2026-05-19T00:00:00", 28800, "eve",
                List.of(recording, hyp));
        when(deviceEventService.loadDeviceEventsByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(eve);
        when(waveformService.loadScanByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(emptyScan());
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.empty());

        WaveformWindowPlan plan = planner.plan(
                "team-1", "2026-05-19", WaveformAnchorSupport.ANCHOR_WORST_OBSTRUCTIVE, 1, 0, null, 15, null);

        assertThat(plan.startMinute()).isEqualTo(266);
        assertThat(plan.reason()).contains("Hypopnea");
    }

    @Test
    void plan_worstLeak_usesNotableMoments() {
        when(waveformService.resolveBrpFileId(eq("team-1"), eq("2026-05-19"))).thenReturn("brp-file");
        when(deviceEventService.loadDeviceEventsByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(emptyEve());
        when(waveformService.loadScanByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(emptyScan());

        ObjectNode night = JsonApi.mapper().createObjectNode();
        ObjectNode moment = night.putArray("notable_moments").addObject();
        moment.put("channel", "leak");
        moment.put("offset_seconds", 5821);
        moment.put("clock", "01:37:01");
        moment.put("value", 42.0);
        moment.put("timestamp", "2026-05-19T01:37:01");
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.of(night));

        WaveformWindowPlan plan = planner.plan(
                "team-1", "2026-05-19", WaveformAnchorSupport.ANCHOR_WORST_LEAK, 1, 0, null, 15, null);

        assertThat(plan.reason().toLowerCase()).contains("leak");
        assertThat(plan.startMinute()).isEqualTo(92);
    }

    @Test
    void plan_worstLeak_noOffsetSeconds_doesNotFallBackToMinuteZero() {
        when(waveformService.resolveBrpFileId(eq("team-1"), eq("2026-05-19"))).thenReturn("brp-file");
        when(deviceEventService.loadDeviceEventsByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(emptyEve());
        when(waveformService.loadScanByDate(eq("team-1"), eq("2026-05-19"), isNull())).thenReturn(emptyScan());

        ObjectNode night = JsonApi.mapper().createObjectNode();
        ObjectNode moment = night.putArray("notable_moments").addObject();
        moment.put("channel", "leak");
        moment.put("clock", "01:37:01");
        moment.put("value", 42.0);
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.of(night));

        assertThatThrownBy(() -> planner.plan(
                "team-1", "2026-05-19", WaveformAnchorSupport.ANCHOR_WORST_LEAK, 1, 0, null, 15, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no_anchor_candidates");
    }

    @Test
    void plan_autoSecondWindow_selectsDistantCluster() {
        when(waveformService.resolveBrpFileId(eq("team-1"), eq("2026-05-19"))).thenReturn("brp-file");

        DeviceEvent late = new DeviceEvent("04:31:00", 16260.0, 10.0, "2026-05-19T04:31:00", "Hypopnea", "H");
        when(deviceEventService.loadDeviceEventsByDate(eq("team-1"), eq("2026-05-19"), isNull()))
                .thenReturn(new DeviceEventResult("EVE.edf", "2026-05-19T00:00:00", 28800, "eve", List.of(late)));

        ApneaEvent scanLate = new ApneaEvent("04:31:00", 16260.0, 10.0, "2026-05-19T04:31:00", "HYPOPNEA");
        when(waveformService.loadScanByDate(eq("team-1"), eq("2026-05-19"), isNull()))
                .thenReturn(new ApneaScanResult("BRP.edf", "2026-05-19T00:00:00", 28800, "Flow", 0.15,
                        List.of(scanLate)));

        ObjectNode night = JsonApi.mapper().createObjectNode();
        ObjectNode leak = night.putArray("notable_moments").addObject();
        leak.put("channel", "leak");
        leak.put("offset_seconds", 5821);
        leak.put("clock", "01:37:01");
        leak.put("value", 50.0);
        when(nightAnalysisService.analyzeNight(eq("2026-05-19"))).thenReturn(Optional.of(night));

        WaveformWindowPlan first = planner.plan("team-1", "2026-05-19", "auto", 2, 0, null, 15, null);
        WaveformWindowPlan second = planner.plan("team-1", "2026-05-19", "auto", 2, 1, null, 15, null);

        assertThat(Math.abs(second.startMinute() - first.startMinute()))
                .isGreaterThanOrEqualTo(WaveformAnchorSupport.SECOND_WINDOW_MIN_GAP_MINUTES);
    }

    private static DeviceEventResult emptyEve() {
        return new DeviceEventResult("", "", 0, "eve", List.of());
    }

    private static ApneaScanResult emptyScan() {
        return new ApneaScanResult("", "", 0, "Flow", 0.15, List.of());
    }
}
