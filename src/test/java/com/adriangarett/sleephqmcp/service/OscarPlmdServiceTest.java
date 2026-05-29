package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OscarPlmdServiceTest {

    @Test
    void plmdNight_doesNotEmitFabricatedBaselineOrDelta() throws Exception {
        OscarRepository repo = mock(OscarRepository.class);
        SleepHqClient client = mock(SleepHqClient.class);
        ClinicalContextProperties clinical = mock(ClinicalContextProperties.class);

        WaveformChannel vt = new WaveformChannel("TidVol", 1.0, "ml", List.of(300.0, 320.0, 340.0));
        WaveformChannel rr = new WaveformChannel("RespRate", 1.0, "bpm", List.of(14.0, 16.0, 18.0));
        WaveformResult pld = new WaveformResult("PLD.edf", "2026-05-01T22:00:00", 3.0, List.of(vt, rr));
        when(repo.loadPldWaveform(LocalDate.parse("2026-05-01"))).thenReturn(Optional.of(pld));
        when(clinical.defaultO2MachineId()).thenReturn(null);

        OscarPlmdService service = new OscarPlmdService(repo, client, clinical);
        JsonNode out = JsonApi.parse(service.plmdNight("2026-05-01", null));

        assertThat(out.has("mean_vt_pld")).isTrue();
        assertThat(out.has("mean_rr_pld")).isTrue();
        assertThat(out.has("mean_vt_baseline")).isFalse();
        assertThat(out.has("mean_rr_baseline")).isFalse();
        assertThat(out.has("vt_delta")).isFalse();
        assertThat(out.has("rr_delta")).isFalse();
        assertThat(out.get("comparison_available").asBoolean()).isFalse();
        // mean([300.0, 320.0, 340.0]) = 320.0 → rounded = 320.0
        assertThat(out.get("mean_vt_pld").asDouble()).isEqualTo(320.0);
        // mean([14.0, 16.0, 18.0]) = 16.0 → rounded = 16.0
        assertThat(out.get("mean_rr_pld").asDouble()).isEqualTo(16.0);
    }

    @Test
    void plmdNight_whenNoPld_returnsUnavailable() throws Exception {
        OscarRepository repo = mock(OscarRepository.class);
        SleepHqClient client = mock(SleepHqClient.class);
        ClinicalContextProperties clinical = mock(ClinicalContextProperties.class);

        when(repo.loadPldWaveform(LocalDate.parse("2026-05-02"))).thenReturn(Optional.empty());

        OscarPlmdService service = new OscarPlmdService(repo, client, clinical);
        JsonNode out = JsonApi.parse(service.plmdNight("2026-05-02", null));

        assertThat(out.get("oscar_status").asText()).isEqualTo("unavailable");
    }
}
