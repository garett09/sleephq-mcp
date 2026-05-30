package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.config.SleepHqLocalProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.PldEdfTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SleepHqNightSummaryServiceTest {

    @Mock private SleepHqClient client;
    @Mock private ApiNightFileSource apiSource;
    @Mock private MachineDateAttributesLoader machineDateAttributesLoader;

    private SleepHqNightSummaryService service(LocalNightFileSource local) {
        return service(local, "");
    }

    private SleepHqNightSummaryService service(LocalNightFileSource local, String syncReportPath) {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-1", "cpap-1", "o2-1", null);
        return new SleepHqNightSummaryService(local, apiSource, client, clinical,
                machineDateAttributesLoader, syncReportPath);
    }

    @Test
    void getNightSummary_localFirst_buildsPressure_recordsLocalSource(@TempDir Path root) throws Exception {
        Path o2 = Files.createTempDirectory("o2");
        Path folder = Files.createDirectories(root.resolve("DATALOG/20260528"));
        List<Double> press = new ArrayList<>();
        for (int i = 0; i < 54; i++) press.add(8.0);
        for (int i = 0; i < 6; i++) press.add(12.0);
        byte[] edf = PldEdfTestSupport.build(1, 60.0,
                List.of(new PldEdfTestSupport.Signal("Press.2s", "cmH2O", 0.0, 25.0, 60)),
                List.of(press));
        Files.write(folder.resolve("20260528_223104_PLD.edf"), edf);

        LocalNightFileSource local = new LocalNightFileSource(
                new SleepHqLocalProperties(root.toString(), o2.toString()));

        when(machineDateAttributesLoader.loadOrNull("2026-05-28")).thenReturn(null);
        lenient().when(client.getMachine("cpap-1")).thenReturn(
                "{ \"data\": { \"attributes\": { \"brand\": \"ResMed\", \"model\": \"AirSense 11\" } } }");
        lenient().when(client.getMachineDateByDate("o2-1", "2026-05-28")).thenReturn("{}");

        String json = service(local).getNightSummary("2026-05-28", null, null);
        JsonNode root2 = JsonApi.parse(json);

        assertThat(root2.path("coverage").path("cpap").asBoolean()).isTrue();
        assertThat(root2.path("coverage").path("oximetry").asBoolean()).isFalse();
        assertThat(root2.path("cpap").path("channels").path("pressure").path("max").asDouble()).isCloseTo(12.0, within(0.01));
        assertThat(root2.path("provenance").path("cpap_source").asText()).isEqualTo("local");
    }

    @Test
    void getNightSummary_emitsLocalMirrorFreshness_fromSyncReport(@TempDir Path root) throws Exception {
        Path o2 = Files.createTempDirectory("o2");
        Path folder = Files.createDirectories(root.resolve("DATALOG/20260528"));
        List<Double> press = new ArrayList<>();
        for (int i = 0; i < 60; i++) press.add(9.0);
        byte[] edf = PldEdfTestSupport.build(1, 60.0,
                List.of(new PldEdfTestSupport.Signal("Press.2s", "cmH2O", 0.0, 25.0, 60)),
                List.of(press));
        Files.write(folder.resolve("20260528_223104_PLD.edf"), edf);

        Path report = Files.createTempFile("sync_report", ".json");
        Files.writeString(report, "{ \"timestamp\": \"2026-05-30 00:49:38 +0800\" }");

        LocalNightFileSource local = new LocalNightFileSource(
                new SleepHqLocalProperties(root.toString(), o2.toString()));
        when(machineDateAttributesLoader.loadOrNull("2026-05-28")).thenReturn(null);
        lenient().when(client.getMachine("cpap-1")).thenReturn("{}");
        lenient().when(client.getMachineDateByDate("o2-1", "2026-05-28")).thenReturn("{}");

        String json = service(local, report.toString()).getNightSummary("2026-05-28", null, null);
        JsonNode prov = JsonApi.parse(json).path("provenance");

        assertThat(prov.path("local_mirror_synced_at").asText()).isEqualTo("2026-05-30 00:49:38 +0800");
        assertThat(prov.path("local_mirror_age_hours").isNumber()).isTrue();
    }

    @Test
    void getNightSummary_fallsBackToApi_whenLocalEmpty(@TempDir Path emptyRoot) throws Exception {
        Path emptyO2 = Files.createTempDirectory("o2empty");
        LocalNightFileSource local = new LocalNightFileSource(
                new SleepHqLocalProperties(emptyRoot.toString(), emptyO2.toString()));

        List<Double> press = new ArrayList<>();
        for (int i = 0; i < 60; i++) press.add(9.0);
        byte[] edf = PldEdfTestSupport.build(1, 60.0,
                List.of(new PldEdfTestSupport.Signal("Press.2s", "cmH2O", 0.0, 25.0, 60)),
                List.of(press));
        when(apiSource.available()).thenReturn(true);
        when(apiSource.label()).thenReturn("sleephq_api");
        when(apiSource.cpapSessions("2026-05-28")).thenReturn(List.of(
                new com.adriangarett.sleephqmcp.domain.NightSessionFile(
                        "20260528_223104_PLD.edf", null, () -> edf)));
        when(apiSource.o2Sessions("2026-05-28")).thenReturn(List.of());
        when(machineDateAttributesLoader.loadOrNull("2026-05-28")).thenReturn(null);
        lenient().when(client.getMachine("cpap-1")).thenReturn("{}");
        lenient().when(client.getMachineDateByDate("o2-1", "2026-05-28")).thenReturn("{}");

        String json = service(local).getNightSummary("2026-05-28", null, null);
        JsonNode out = JsonApi.parse(json);

        assertThat(out.path("coverage").path("cpap").asBoolean()).isTrue();
        assertThat(out.path("provenance").path("cpap_source").asText()).isEqualTo("sleephq_api");
        assertThat(out.path("cpap").path("channels").path("pressure").path("median").asDouble()).isCloseTo(9.0, within(0.01));
    }

    @Test
    void getNightSummary_noData_throws(@TempDir Path emptyRoot) throws Exception {
        Path emptyO2 = Files.createTempDirectory("o2empty2");
        LocalNightFileSource local = new LocalNightFileSource(
                new SleepHqLocalProperties(emptyRoot.toString(), emptyO2.toString()));
        when(apiSource.available()).thenReturn(true);
        when(apiSource.cpapSessions("2026-05-28")).thenReturn(List.of());
        when(apiSource.o2Sessions("2026-05-28")).thenReturn(List.of());

        assertThatThrownBy(() -> service(local).getNightSummary("2026-05-28", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no_sleephq_data_for_date");
    }
}
