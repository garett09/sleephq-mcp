package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.NightChannelSummary;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NightSummaryComputerRealPldTest {

    @Test
    void realPld_mapsVerifiedChannels_andConvertsUnits() throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/edf/real_pld_sample.edf")) {
            assertThat(in).as("real_pld_sample.edf fixture present").isNotNull();
            bytes = in.readAllBytes();
        }

        WaveformResult parsed = EdfParser.parse(bytes, 0, 12 * 3600);

        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        for (WaveformChannel ch : parsed.channels()) {
            String field = NightSummaryComputer.mapPldLabel(ch.label());
            if (field == null || ch.samples().isEmpty()) {
                continue;
            }
            NightChannelSummary s = NightSummaryComputer.summarise(field, ch.unit(), ch.samples(), ch.sampleRate());
            if (s != null) {
                channels.put(field, s);
            }
        }

        assertThat(channels).containsKeys(
                "mask_pressure", "pressure", "epap", "leak_rate", "resp_rate",
                "tidal_volume", "minute_vent", "snore", "flow_limit");
        assertThat(channels.get("leak_rate").unit()).isEqualTo("L/min");
        assertThat(channels.get("tidal_volume").unit()).isEqualTo("mL");
        assertThat(channels.get("pressure").unit()).isEqualTo("cmH2O");
        assertThat(channels.get("tidal_volume").median()).isBetween(100.0, 1500.0); // mL
        assertThat(channels.get("resp_rate").median()).isBetween(5.0, 40.0);        // bpm
    }
}
