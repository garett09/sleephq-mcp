package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OscarChannelMapperTest {

    @Test
    void mapsKnownClinicalChannels() {
        assertThat(OscarChannelMapper.fieldName("RespRate")).isEqualTo("resp_rate");
        assertThat(OscarChannelMapper.fieldName("TidalVolume")).isEqualTo("tidal_volume");
        assertThat(OscarChannelMapper.fieldName("MinuteVent")).isEqualTo("minute_vent");
        assertThat(OscarChannelMapper.fieldName("Ti")).isEqualTo("insp_time");
        assertThat(OscarChannelMapper.fieldName("Te")).isEqualTo("exp_time");
        assertThat(OscarChannelMapper.fieldName("FLG")).isEqualTo("flow_limit");
        assertThat(OscarChannelMapper.fieldName("Leak")).isEqualTo("leak");
        assertThat(OscarChannelMapper.fieldName("LeakTotal")).isEqualTo("leak_total");
        assertThat(OscarChannelMapper.fieldName("Pressure")).isEqualTo("pressure");
        assertThat(OscarChannelMapper.fieldName("EPAP")).isEqualTo("epap");
        assertThat(OscarChannelMapper.fieldName("IPAP")).isEqualTo("ipap");
        assertThat(OscarChannelMapper.fieldName("Snore")).isEqualTo("snore");
        assertThat(OscarChannelMapper.fieldName("FlowRate")).isEqualTo("flow_brp");
        assertThat(OscarChannelMapper.fieldName("MaskPressure")).isEqualTo("mask_pressure");
        assertThat(OscarChannelMapper.fieldName("AHI")).isEqualTo("ahi");
        assertThat(OscarChannelMapper.fieldName("SPO2")).isEqualTo("spo2");
        assertThat(OscarChannelMapper.fieldName("PulseRate")).isEqualTo("pulse_rate");
    }

    @Test
    void unknownChannelReturnsSnakeCasedCode() {
        assertThat(OscarChannelMapper.fieldName("UnknownXYZ")).isEqualTo("unknown_xyz");
    }

    @Test
    void unitForKnownChannels() {
        assertThat(OscarChannelMapper.unit("RespRate")).isEqualTo("BPM");
        assertThat(OscarChannelMapper.unit("TidalVolume")).isEqualTo("ml");
        assertThat(OscarChannelMapper.unit("Pressure")).isEqualTo("cmH2O");
        assertThat(OscarChannelMapper.unit("Leak")).isEqualTo("L/min");
        assertThat(OscarChannelMapper.unit("Ti")).isEqualTo("s");
        assertThat(OscarChannelMapper.unit("MinuteVent")).isEqualTo("L/min");
        assertThat(OscarChannelMapper.unit("SPO2")).isEqualTo("%");
    }

    @Test
    void isEventChannel() {
        assertThat(OscarChannelMapper.isEventChannel("Obstructive")).isTrue();
        assertThat(OscarChannelMapper.isEventChannel("ClearAirway")).isTrue();
        assertThat(OscarChannelMapper.isEventChannel("Hypopnea")).isTrue();
        assertThat(OscarChannelMapper.isEventChannel("RERA")).isTrue();
        assertThat(OscarChannelMapper.isEventChannel("Apnea")).isTrue();
        assertThat(OscarChannelMapper.isEventChannel("RespRate")).isFalse();
        assertThat(OscarChannelMapper.isEventChannel("Pressure")).isFalse();
    }

    @Test
    void canonicalEventLabel() {
        assertThat(OscarChannelMapper.canonicalEventLabel("Obstructive")).isEqualTo("obstructive");
        assertThat(OscarChannelMapper.canonicalEventLabel("ClearAirway")).isEqualTo("clear_airway");
        assertThat(OscarChannelMapper.canonicalEventLabel("Hypopnea")).isEqualTo("hypopnea");
        assertThat(OscarChannelMapper.canonicalEventLabel("RERA")).isEqualTo("rera");
        assertThat(OscarChannelMapper.canonicalEventLabel("Apnea")).isEqualTo("apnea");
        assertThat(OscarChannelMapper.canonicalEventLabel("Pressure")).isNull();
    }
}
