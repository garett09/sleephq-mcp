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
                        "20260528_223104_PLD.edf", null, () -> edf, "api-file-1")));
        when(apiSource.o2Sessions("2026-05-28")).thenReturn(List.of());
        when(machineDateAttributesLoader.loadOrNull("2026-05-28")).thenReturn(null);
        lenient().when(client.getMachine("cpap-1")).thenReturn("{}");
        lenient().when(client.getMachineDateByDate("o2-1", "2026-05-28")).thenReturn("{}");

        String json = service(local).getNightSummary("2026-05-28", null, null);
        JsonNode out = JsonApi.parse(json);

        assertThat(out.path("coverage").path("cpap").asBoolean()).isTrue();
        assertThat(out.path("provenance").path("cpap_source").asText()).isEqualTo("sleephq_api");
        assertThat(out.path("cpap").path("channels").path("pressure").path("median").asDouble()).isCloseTo(9.0, within(0.01));
        JsonNode session = out.path("provenance").path("cpap_sessions").get(0);
        assertThat(session.path("filename").asText()).isEqualTo("20260528_223104_PLD.edf");
        assertThat(session.path("file_id").asText()).isEqualTo("api-file-1");
        assertThat(session.has("name")).isFalse();
    }

    @Test
    void getNightSummary_fallsBackToApi_whenLocalPldSessionTooShort(@TempDir Path root) throws Exception {
        Path o2 = Files.createTempDirectory("o2");
        Path folder = Files.createDirectories(root.resolve("DATALOG/20260529"));
        List<Double> leakLps = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            leakLps.add(0.04);
        }
        byte[] shortEdf = PldEdfTestSupport.build(10, 60.0,
                List.of(new PldEdfTestSupport.Signal("Leak.2s", "", 0.0, 2.0, 30)),
                List.of(leakLps));
        Files.write(folder.resolve("20260529_010000_PLD.edf"), shortEdf);

        List<Double> leakTherapy = new ArrayList<>();
        for (int i = 0; i < 7200; i++) {
            leakTherapy.add(0.22);
        }
        byte[] fullEdf = PldEdfTestSupport.build(120, 60.0,
                List.of(new PldEdfTestSupport.Signal("Leak.2s", "", 0.0, 2.0, 60)),
                List.of(leakTherapy));

        when(apiSource.available()).thenReturn(true);
        when(apiSource.label()).thenReturn("sleephq_api");
        when(apiSource.cpapSessions("2026-05-29")).thenReturn(List.of(
                new com.adriangarett.sleephqmcp.domain.NightSessionFile(
                        "20260529_012500_PLD.edf", null, () -> fullEdf, "api-pld-1")));
        when(apiSource.o2Sessions("2026-05-29")).thenReturn(List.of());
        when(machineDateAttributesLoader.loadOrNull("2026-05-29")).thenReturn(null);
        lenient().when(client.getMachine("cpap-1")).thenReturn("{}");
        lenient().when(client.getMachineDateByDate("o2-1", "2026-05-29")).thenReturn("{}");

        LocalNightFileSource local = new LocalNightFileSource(
                new SleepHqLocalProperties(root.toString(), o2.toString()));
        String json = service(local).getNightSummary("2026-05-29", null, null);
        JsonNode out = JsonApi.parse(json);

        assertThat(out.path("provenance").path("cpap_source").asText()).isEqualTo("sleephq_api");
        assertThat(out.path("provenance").path("cpap_local_skipped_reason").asText())
                .isEqualTo("local_session_too_short");
        assertThat(out.path("provenance").path("cpap_local_analysed_seconds").asInt()).isEqualTo(600);
        assertThat(out.path("cpap").path("channels").path("leak_rate").path("p95").asDouble())
                .isCloseTo(13.2, within(0.5));
    }

    @Test
    void getNightSummary_noCpap_setsCpapReason_whenO2Present(@TempDir Path root) throws Exception {
        Path o2Dir = Files.createTempDirectory("o2");
        byte[] o2Bytes = com.adriangarett.sleephqmcp.support.ViatomTestFixtures.o2RingSSession();
        Files.write(o2Dir.resolve("20260528120000-1721"), o2Bytes);

        LocalNightFileSource local = new LocalNightFileSource(
                new SleepHqLocalProperties(root.toString(), o2Dir.toString()));
        when(machineDateAttributesLoader.loadOrNull("2026-05-28")).thenReturn(null);
        lenient().when(client.getMachine("cpap-1")).thenReturn("{}");
        lenient().when(client.getMachineDateByDate("o2-1", "2026-05-28")).thenReturn("{}");

        String json = service(local).getNightSummary("2026-05-28", null, null);
        JsonNode out = JsonApi.parse(json);

        assertThat(out.path("coverage").path("cpap").asBoolean()).isFalse();
        assertThat(out.path("coverage").path("cpap_reason").asText()).isEqualTo("no_sleephq_pld");
        assertThat(out.path("coverage").path("oximetry").asBoolean()).isTrue();
        assertThat(out.has("cpap")).isFalse();
        assertThat(out.has("oximetry")).isTrue();
    }

    @Test
    void getNightSummary_concatenatesTwoPldSessions_intoOneDistribution(@TempDir Path root) throws Exception {
        Path o2 = Files.createTempDirectory("o2");
        Path folder = Files.createDirectories(root.resolve("DATALOG/20260528"));
        List<Double> low = new ArrayList<>();
        List<Double> high = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            low.add(8.0);
        }
        for (int i = 0; i < 50; i++) {
            high.add(12.0);
        }
        byte[] edf1 = PldEdfTestSupport.build(1, 50.0,
                List.of(new PldEdfTestSupport.Signal("Press.2s", "cmH2O", 0.0, 25.0, 50)),
                List.of(low));
        byte[] edf2 = PldEdfTestSupport.build(1, 50.0,
                List.of(new PldEdfTestSupport.Signal("Press.2s", "cmH2O", 0.0, 25.0, 50)),
                List.of(high));
        Files.write(folder.resolve("20260528_220000_PLD.edf"), edf1);
        Files.write(folder.resolve("20260528_230000_PLD.edf"), edf2);

        LocalNightFileSource local = new LocalNightFileSource(
                new SleepHqLocalProperties(root.toString(), o2.toString()));
        when(machineDateAttributesLoader.loadOrNull("2026-05-28")).thenReturn(null);
        lenient().when(client.getMachine("cpap-1")).thenReturn("{}");
        lenient().when(client.getMachineDateByDate("o2-1", "2026-05-28")).thenReturn("{}");

        String json = service(local).getNightSummary("2026-05-28", null, null);
        JsonNode out = JsonApi.parse(json);

        assertThat(out.path("cpap").path("channels").path("pressure").path("count").asInt()).isEqualTo(100);
        assertThat(out.path("cpap").path("channels").path("pressure").path("min").asDouble()).isCloseTo(8.0, within(0.01));
        assertThat(out.path("cpap").path("channels").path("pressure").path("max").asDouble()).isCloseTo(12.0, within(0.01));
        assertThat(out.path("cpap").path("channels").path("pressure").path("median").asDouble()).isCloseTo(8.0, within(0.01));
        assertThat(out.path("provenance").path("cpap_sessions")).hasSize(2);
        assertThat(out.path("provenance").path("cpap_sessions").get(0).path("filename").asText())
                .endsWith("_PLD.edf");
        assertThat(out.path("provenance").path("session_count").asInt()).isEqualTo(2);
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
