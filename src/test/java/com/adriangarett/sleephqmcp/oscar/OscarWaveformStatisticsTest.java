package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OscarWaveformStatisticsTest {

    @Test
    void mapLabelToField_flowHiRes_variants() {
        assertEquals("flow_rate_hi_res", OscarWaveformStatistics.mapLabelToField("FlowHiRes"));
        assertEquals("flow_rate_hi_res", OscarWaveformStatistics.mapLabelToField("FlowRate2"));
        assertEquals("flow_rate_hi_res", OscarWaveformStatistics.mapLabelToField("Flow rate (hi-res)"));
    }

    @Test
    void mapLabelToField_existingBehaviourUnchanged() {
        assertEquals("flow", OscarWaveformStatistics.mapLabelToField("FlowRate"));
        assertEquals("tidal_volume", OscarWaveformStatistics.mapLabelToField("TidVol"));
    }

    @Test
    void mapLabelToField_newMappings_eprPressAndMaskPress() {
        assertEquals("epap",          OscarWaveformStatistics.mapLabelToField("EprPress.2s"));
        assertEquals("epap",          OscarWaveformStatistics.mapLabelToField("EpapRes.2s"));
        assertEquals("mask_pressure", OscarWaveformStatistics.mapLabelToField("MaskPress.2s"));
    }

    @Test
    void mapLabelToField_newMappings_snoreAndFlowLim() {
        assertEquals("snore",       OscarWaveformStatistics.mapLabelToField("Snore.2s"));
        assertEquals("flow_limit",  OscarWaveformStatistics.mapLabelToField("FlowLim.2s"));
    }

    @Test
    void mapLabelToField_newMappings_inspAndExpTime() {
        assertEquals("insp_time", OscarWaveformStatistics.mapLabelToField("Ti.1s"));
        assertEquals("insp_time", OscarWaveformStatistics.mapLabelToField("Ti"));
        assertEquals("exp_time",  OscarWaveformStatistics.mapLabelToField("Te.1s"));
        assertEquals("exp_time",  OscarWaveformStatistics.mapLabelToField("Te"));
    }

    @Test
    void mapLabelToField_pressStillMapsToPressure_whenNoPrefixMatch() {
        assertEquals("pressure", OscarWaveformStatistics.mapLabelToField("Press.2s"));
    }

    @Test
    void mapLabelToField_tidVolStillWins_notInspTime() {
        // "TidVol" starts with "ti" but must remain tidal_volume, not insp_time
        assertEquals("tidal_volume", OscarWaveformStatistics.mapLabelToField("TidVol.2s"));
    }

    @Test
    void mapLabelToField_flowLimBeforeFlowCatchAll() {
        // "FlowLim" starts with "flow" — must not fall through to "flow"
        assertEquals("flow_limit", OscarWaveformStatistics.mapLabelToField("FlowLim.2s"));
        assertEquals("flow",       OscarWaveformStatistics.mapLabelToField("FlowRate.2s"));
    }
}
