package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.config.SleepHqObservabilityProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.PhaseTiming;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaveformServiceTest {

    @Mock
    private SleepHqCacheFacade cacheFacade;

    @Mock
    private MachineDateTimeOffsetLoader machineDateTimeOffsetLoader;

    private WaveformService waveformService;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-123", "cpap-123", "o2-123", null);
        waveformService = new WaveformService(cacheFacade, clinical, machineDateTimeOffsetLoader,
                new PhaseTiming(new SleepHqObservabilityProperties(false)));
        when(cacheFacade.getCachedApneaScanJson(anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());
    }

    @Test
    void scanApneaEvents_detectsObstructiveApnea() throws Exception {
        // 1. Mock file info response
        String fileMetadataJson = """
                {
                  "data": {
                    "id": "file-abc",
                    "type": "import_file",
                    "attributes": {
                      "name": "20260520_210920_BRP.edf",
                      "download_url": "https://s3.amazonaws.com/test-bucket/file-abc"
                    }
                  }
                }
                """;
        when(cacheFacade.getImportFile("file-abc")).thenReturn(fileMetadataJson);

        // 2. Build mock EDF:
        //    nRecords = 30, recDuration = 1.0, samplesPerRec = 25 (25 Hz)
        //    Record 0 to 3: normal flow (around 0.5)
        //    Record 4 to 23: obstructive apnea flow (around 0.01 L/s, no 4Hz)
        //    Record 24 to 29: normal flow (around 0.5)
        byte[] edf = new byte[256 + 256 + 30 * 25 * 2];
        Arrays.fill(edf, (byte) ' ');

        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, "30");
        writeString(edf, 244, "1.0");
        writeString(edf, 252, "1");

        writeString(edf, 256, "Flow.40ms");
        writeString(edf, 256 + 96, "L/s");
        writeString(edf, 256 + 104, "-5.0");
        writeString(edf, 256 + 112, "5.0");
        writeString(edf, 256 + 120, "-2048");
        writeString(edf, 256 + 128, "2047");
        writeString(edf, 256 + 216, "25");

        int pos = 512;
        for (int i = 0; i < 30 * 25; i++) {
            boolean isApnea = (i >= 100 && i < 600);
            short rawVal = (short) (isApnea ? 3 : 204); // 3 -> ~0.0085 L/s (Apnea), 204 -> ~0.499 L/s (Normal)
            edf[pos++] = (byte) (rawVal & 0xFF);
            edf[pos++] = (byte) ((rawVal >> 8) & 0xFF);
        }

        when(cacheFacade.downloadEdf(any(URI.class), eq("file-abc"))).thenReturn(edf);

        // Run scanner (threshold = 0.15 for hypopnea, but average of 0.0085 L/s will classify as APNEA)
        String resultJson = waveformService.scanApneaEvents("file-abc", null, null, 0.15, 8);

        JsonNode root = JsonApi.parse(resultJson);
        JsonNode events = root.path("events");
        assertThat(events).hasSize(1);

        JsonNode event = events.get(0);
        assertThat(event.path("classification").asText()).isEqualTo("APNEA_OBSTRUCTIVE");
        assertThat(event.path("duration_seconds").asDouble()).isEqualTo(18.28);
        assertThat(event.path("start_seconds").asDouble()).isEqualTo(6.84);
        assertThat(event.path("offset").asText()).isEqualTo("00:00:06");
        assertThat(event.path("timestamp").asText()).isEqualTo("2026-05-20T21:09:06.840");
    }

    @Test
    void scanApneaEvents_withMachineDateTimeOffset_shiftsTimestamps() throws Exception {
        when(machineDateTimeOffsetLoader.loadForCpapDate("2026-05-20", null))
                .thenReturn(java.util.OptionalInt.of(1428));

        String fileMetadataJson = """
                {
                  "data": {
                    "id": "file-abc",
                    "type": "import_file",
                    "attributes": {
                      "name": "20260520_210920_BRP.edf",
                      "download_url": "https://s3.amazonaws.com/test-bucket/file-abc"
                    }
                  }
                }
                """;
        when(cacheFacade.getImportFile("file-abc")).thenReturn(fileMetadataJson);

        byte[] edf = new byte[256 + 256 + 30 * 25 * 2];
        Arrays.fill(edf, (byte) ' ');
        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, "30");
        writeString(edf, 244, "1.0");
        writeString(edf, 252, "1");
        writeString(edf, 256, "Flow.40ms");
        writeString(edf, 256 + 96, "L/s");
        writeString(edf, 256 + 104, "-5.0");
        writeString(edf, 256 + 112, "5.0");
        writeString(edf, 256 + 120, "-2048");
        writeString(edf, 256 + 128, "2047");
        writeString(edf, 256 + 216, "25");

        int pos = 512;
        for (int i = 0; i < 30 * 25; i++) {
            boolean isApnea = (i >= 100 && i < 600);
            short rawVal = (short) (isApnea ? 3 : 204);
            edf[pos++] = (byte) (rawVal & 0xFF);
            edf[pos++] = (byte) ((rawVal >> 8) & 0xFF);
        }

        when(cacheFacade.downloadEdf(any(URI.class), eq("file-abc"))).thenReturn(edf);

        String resultJson = waveformService.scanApneaEvents("file-abc", null, "2026-05-20", 0.15, 8, null);

        JsonNode root = JsonApi.parse(resultJson);
        assertThat(root.path("clock_alignment").path("cpap_adjust_seconds").asInt()).isEqualTo(1428);
        assertThat(root.path("clock_alignment").path("source").asText()).isEqualTo("sleephq_machine_date");
        assertThat(root.path("events").get(0).path("timestamp").asText()).isEqualTo("2026-05-20T21:32:54.840");
        assertThat(root.path("events").get(0).path("offset").asText()).isEqualTo("00:00:06");
    }

    @Test
    void scanApneaEvents_detectsCentralApnea() throws Exception {
        String fileMetadataJson = """
                {
                  "data": {
                    "id": "file-abc",
                    "type": "import_file",
                    "attributes": {
                      "name": "20260520_210920_BRP.edf",
                      "download_url": "https://s3.amazonaws.com/test-bucket/file-abc"
                    }
                  }
                }
                """;
        when(cacheFacade.getImportFile("file-abc")).thenReturn(fileMetadataJson);

        // Build mock EDF with 4 Hz Forced Oscillation Technique (FOT) wave added during apnea
        byte[] edf = new byte[256 + 256 + 30 * 25 * 2];
        Arrays.fill(edf, (byte) ' ');

        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, "30");
        writeString(edf, 244, "1.0");
        writeString(edf, 252, "1");

        writeString(edf, 256, "Flow.40ms");
        writeString(edf, 256 + 96, "L/s");
        writeString(edf, 256 + 104, "-5.0");
        writeString(edf, 256 + 112, "5.0");
        writeString(edf, 256 + 120, "-2048");
        writeString(edf, 256 + 128, "2047");
        writeString(edf, 256 + 216, "25");

        int pos = 512;
        for (int i = 0; i < 30 * 25; i++) {
            boolean isApnea = (i >= 100 && i < 600);
            double physVal;
            if (isApnea) {
                // Add a strong 4 Hz sinusoidal wave: amplitude = 0.008 L/s
                physVal = 0.008 * Math.sin(2.0 * Math.PI * 4.0 * (i - 100) / 25.0);
            } else {
                physVal = 0.5;
            }
            // Scale to digital raw
            short rawVal = (short) Math.round((physVal + 5.0) * 4095.0 / 10.0 - 2048.0);
            edf[pos++] = (byte) (rawVal & 0xFF);
            edf[pos++] = (byte) ((rawVal >> 8) & 0xFF);
        }

        when(cacheFacade.downloadEdf(any(URI.class), eq("file-abc"))).thenReturn(edf);

        String resultJson = waveformService.scanApneaEvents("file-abc", null, null, 0.15, 8);

        JsonNode root = JsonApi.parse(resultJson);
        JsonNode events = root.path("events");
        assertThat(events).hasSize(1);

        JsonNode event = events.get(0);
        assertThat(event.path("classification").asText()).isEqualTo("APNEA_CENTRAL");
        assertThat(event.path("duration_seconds").asDouble()).isEqualTo(18.36);
        assertThat(event.path("start_seconds").asDouble()).isEqualTo(6.8);
        assertThat(event.path("offset").asText()).isEqualTo("00:00:06");
        assertThat(event.path("timestamp").asText()).isEqualTo("2026-05-20T21:09:06.800");
    }

    @Test
    void scanApneaEvents_detectsHypopnea() throws Exception {
        String fileMetadataJson = """
                {
                  "data": {
                    "id": "file-abc",
                    "type": "import_file",
                    "attributes": {
                      "name": "20260520_210920_BRP.edf",
                      "download_url": "https://s3.amazonaws.com/test-bucket/file-abc"
                    }
                  }
                }
                """;
        when(cacheFacade.getImportFile("file-abc")).thenReturn(fileMetadataJson);

        // Build mock EDF with partial flow reduction (around 0.08 L/s, which is between 0.04 and 0.15)
        byte[] edf = new byte[256 + 256 + 30 * 25 * 2];
        Arrays.fill(edf, (byte) ' ');

        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, "30");
        writeString(edf, 244, "1.0");
        writeString(edf, 252, "1");

        writeString(edf, 256, "Flow.40ms");
        writeString(edf, 256 + 96, "L/s");
        writeString(edf, 256 + 104, "-5.0");
        writeString(edf, 256 + 112, "5.0");
        writeString(edf, 256 + 120, "-2048");
        writeString(edf, 256 + 128, "2047");
        writeString(edf, 256 + 216, "25");

        int pos = 512;
        for (int i = 0; i < 30 * 25; i++) {
            boolean isHypopnea = (i >= 100 && i < 600);
            double physVal = isHypopnea ? 0.08 : 0.5; // 0.08 L/s is Hypopnea
            short rawVal = (short) Math.round((physVal + 5.0) * 4095.0 / 10.0 - 2048.0);
            edf[pos++] = (byte) (rawVal & 0xFF);
            edf[pos++] = (byte) ((rawVal >> 8) & 0xFF);
        }

        when(cacheFacade.downloadEdf(any(URI.class), eq("file-abc"))).thenReturn(edf);

        String resultJson = waveformService.scanApneaEvents("file-abc", null, null, 0.15, 8);

        JsonNode root = JsonApi.parse(resultJson);
        JsonNode events = root.path("events");
        assertThat(events).hasSize(1);

        JsonNode event = events.get(0);
        assertThat(event.path("classification").asText()).isEqualTo("HYPOPNEA");
        assertThat(event.path("duration_seconds").asDouble()).isEqualTo(17.32);
        assertThat(event.path("start_seconds").asDouble()).isEqualTo(7.32);
        assertThat(event.path("offset").asText()).isEqualTo("00:00:07");
        assertThat(event.path("timestamp").asText()).isEqualTo("2026-05-20T21:09:07.320");
    }

    private void writeString(byte[] buf, int offset, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
    }
}
