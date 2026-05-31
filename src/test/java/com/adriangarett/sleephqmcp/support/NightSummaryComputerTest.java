package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.NightChannelSummary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NightSummaryComputerTest {

    @Test
    void mapPldLabel_mapsVerifiedResMedLabels() {
        assertThat(NightSummaryComputer.mapPldLabel("MaskPress.2s")).isEqualTo("mask_pressure");
        assertThat(NightSummaryComputer.mapPldLabel("EprPress.2s")).isEqualTo("epap");
        assertThat(NightSummaryComputer.mapPldLabel("Press.2s")).isEqualTo("pressure");
        assertThat(NightSummaryComputer.mapPldLabel("Leak.2s")).isEqualTo("leak_rate");
        assertThat(NightSummaryComputer.mapPldLabel("RespRate.2s")).isEqualTo("resp_rate");
        assertThat(NightSummaryComputer.mapPldLabel("TidVol.2s")).isEqualTo("tidal_volume");
        assertThat(NightSummaryComputer.mapPldLabel("MinVent.2s")).isEqualTo("minute_vent");
        assertThat(NightSummaryComputer.mapPldLabel("Snore.2s")).isEqualTo("snore");
        assertThat(NightSummaryComputer.mapPldLabel("FlowLim.2s")).isEqualTo("flow_limit");
        assertThat(NightSummaryComputer.mapPldLabel("Crc16")).isNull();
        assertThat(NightSummaryComputer.mapPldLabel("Flow.40ms")).isNull();
    }

    @Test
    void summarise_leakRate_convertsLpsToLpm_andComputesMarkers() {
        List<Double> raw = new ArrayList<>();
        for (int i = 0; i < 90; i++) raw.add(0.1);   // 6 L/min
        for (int i = 0; i < 10; i++) raw.add(0.5);   // 30 L/min
        NightChannelSummary s = NightSummaryComputer.summarise("leak_rate", "L/s", raw, 0.5);
        assertThat(s.unit()).isEqualTo("L/min");
        assertThat(s.max()).isCloseTo(30.0, within(0.01));
        assertThat(s.p95()).isCloseTo(30.0, within(0.01));
        assertThat(s.median()).isCloseTo(6.0, within(0.01));
        assertThat(s.count()).isEqualTo(100);
        assertThat(s.markers().get("time_above_24_l_min_seconds")).isCloseTo(20.0, within(0.01));
        assertThat(s.markers().get("time_above_24_l_min_pct")).isCloseTo(10.0, within(0.01));
    }

    @Test
    void summarise_leakRate_blankPldUnit_infersLps_matchesOscarStyleP95() {
        List<Double> raw = new ArrayList<>();
        for (int i = 0; i < 900; i++) {
            raw.add(0.22);
        }
        for (int i = 0; i < 100; i++) {
            raw.add(0.5);
        }
        NightChannelSummary s = NightSummaryComputer.summarise("leak_rate", "", raw, 0.5);
        assertThat(s.unit()).isEqualTo("L/min");
        assertThat(s.p95()).isCloseTo(30.0, within(0.2));
        assertThat(s.max()).isCloseTo(30.0, within(0.2));
    }

    @Test
    void summarise_spo2_computesNadirAndT88() {
        List<Double> spo2 = new ArrayList<>();
        for (int i = 0; i < 95; i++) spo2.add(95.0);
        for (int i = 0; i < 5; i++) spo2.add(85.0);
        NightChannelSummary s = NightSummaryComputer.summarise("spo2", "%", spo2, 1.0);
        assertThat(s.markers().get("nadir")).isEqualTo(85.0);
        assertThat(s.markers().get("time_below_88_pct_seconds")).isCloseTo(5.0, within(0.01));
        assertThat(s.markers().get("time_below_88_pct")).isCloseTo(5.0, within(0.01));
    }

    @Test
    void summarise_pressure_computesTimeAtMax() {
        List<Double> p = new ArrayList<>();
        for (int i = 0; i < 90; i++) p.add(8.0);
        for (int i = 0; i < 10; i++) p.add(12.0);
        NightChannelSummary s = NightSummaryComputer.summarise("pressure", "cmH2O", p, 0.5);
        assertThat(s.markers().get("max_pressure")).isEqualTo(12.0);
        assertThat(s.markers().get("time_at_max_seconds")).isCloseTo(20.0, within(0.01));
    }

    @Test
    void summarise_pressure_p99_5_isPresentAndPlausible() {
        List<Double> p = new ArrayList<>();
        for (int i = 0; i < 90; i++) p.add(8.0);
        for (int i = 0; i < 10; i++) p.add(12.0);
        NightChannelSummary s = NightSummaryComputer.summarise("pressure", "cmH2O", p, 0.5);
        assertThat(s.p995()).isGreaterThanOrEqualTo(s.p95());
        assertThat(s.p995()).isCloseTo(12.0, within(0.01));
    }

    @Test
    void summarise_emptySamples_returnsNull() {
        assertThat(NightSummaryComputer.summarise("pressure", "cmH2O", List.of(), 0.5)).isNull();
    }

    @Test
    void summarise_concatenatedSessions_matchesSingleDistributionPass() {
        List<Double> brief = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            brief.add(2.0);
        }
        List<Double> therapy = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            therapy.add(12.0);
        }
        List<Double> all = new ArrayList<>(brief);
        all.addAll(therapy);
        NightChannelSummary merged = NightSummaryComputer.summarise("pressure", "cmH2O", all, 0.5);
        assertThat(merged.p95()).isEqualTo(12.0);
        assertThat(merged.median()).isEqualTo(12.0);
        assertThat(merged.count()).isEqualTo(520);
        assertThat(merged.min()).isEqualTo(2.0);
        assertThat(merged.max()).isEqualTo(12.0);
    }

    @Test
    void mapPldLabel_inspAndExpTime() {
        assertThat(NightSummaryComputer.mapPldLabel("Ti.1s")).isEqualTo("insp_time");
        assertThat(NightSummaryComputer.mapPldLabel("Ti")).isEqualTo("insp_time");
        assertThat(NightSummaryComputer.mapPldLabel("Te.1s")).isEqualTo("exp_time");
        assertThat(NightSummaryComputer.mapPldLabel("Te")).isEqualTo("exp_time");
    }

    @Test
    void mapPldLabel_epapresAlias() {
        assertThat(NightSummaryComputer.mapPldLabel("EpapRes.2s")).isEqualTo("epap");
    }
}
