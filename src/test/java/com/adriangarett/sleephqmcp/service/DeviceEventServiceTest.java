package com.adriangarett.sleephqmcp.service;

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

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEventServiceTest {

    @Mock
    private SleepHqCacheFacade cacheFacade;

    @Mock
    private MachineDateTimeOffsetLoader machineDateTimeOffsetLoader;

    private DeviceEventService deviceEventService;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-123", "cpap-1", "o2-1", null);
        deviceEventService = new DeviceEventService(cacheFacade, clinical, machineDateTimeOffsetLoader,
                new PhaseTiming(new SleepHqObservabilityProperties(false)));
        org.mockito.Mockito.when(cacheFacade.getCachedDeviceEventsJson(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(1, java.util.function.Supplier.class).get());
    }

    @Test
    void getDeviceEvents_parsesEveAnnotations() {
        byte[] edf = buildMinimalEveEdf();
        when(cacheFacade.getImportFile("file-1")).thenReturn("""
                {
                  "data": {
                    "attributes": {
                      "name": "20260512_EVE.edf",
                      "download_url": "https://s3.example.com/eve.edf?sig=1"
                    }
                  }
                }
                """);
        when(cacheFacade.downloadEdf(any(URI.class), eq("file-1"))).thenReturn(edf);

        String json = deviceEventService.getDeviceEvents("file-1");
        assertThat(json).contains("\"source\":\"device_eve\"");
        assertThat(json).contains("\"code\":\"H\"");
        assertThat(json).contains("Hypopnea");
    }

    @Test
    void getDeviceEvents_withMachineDateTimeOffset_shiftsWallClockTimestamps() {
        byte[] edf = buildMinimalEveEdf();
        when(cacheFacade.getImportFile("file-1")).thenReturn("""
                {
                  "data": {
                    "attributes": {
                      "name": "20260512_EVE.edf",
                      "download_url": "https://s3.example.com/eve.edf?sig=1"
                    }
                  }
                }
                """);
        when(cacheFacade.downloadEdf(any(URI.class), eq("file-1"))).thenReturn(edf);

        JsonNode root = JsonApi.parse(deviceEventService.getDeviceEvents("file-1", null, java.util.OptionalInt.of(1428)));
        assertThat(root.path("start_datetime").asText()).isEqualTo("2026-05-12T23:23:48");
        assertThat(root.path("events").get(0).path("timestamp").asText()).isEqualTo("2026-05-12T23:23:58");
        assertThat(root.path("events").get(0).path("offset").asText()).isEqualTo("00:00:10");
        assertThat(root.path("clock_alignment").path("cpap_adjust_seconds").asInt()).isEqualTo(1428);
        assertThat(root.path("clock_alignment").path("source").asText()).isEqualTo("sleephq_machine_date");
    }

    private static byte[] buildMinimalEveEdf() {
        String tal = "+10\u00155\u0014Hypopnea\u0014\u0000";
        byte[] talBytes = tal.getBytes(StandardCharsets.US_ASCII);
        int annSamples = (talBytes.length + 1) / 2 + 2;
        byte[] edf = new byte[512 + annSamples * 2];
        java.util.Arrays.fill(edf, (byte) ' ');
        write(edf, 0, "0");
        write(edf, 168, "12.05.26");
        write(edf, 176, "23.00.00");
        write(edf, 184, "512");
        write(edf, 236, "1");
        write(edf, 244, "0");
        write(edf, 252, "1");
        write(edf, 256, "EDF Annotations");
        write(edf, 256 + 120, "-32768");
        write(edf, 256 + 128, "32767");
        write(edf, 256 + 216, String.valueOf(annSamples));
        System.arraycopy(talBytes, 0, edf, 512, talBytes.length);
        return edf;
    }

    private static void write(byte[] buf, int offset, String s) {
        System.arraycopy(s.getBytes(StandardCharsets.US_ASCII), 0, buf, offset, s.length());
    }
}
