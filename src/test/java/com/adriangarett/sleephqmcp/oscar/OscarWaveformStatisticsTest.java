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
}
