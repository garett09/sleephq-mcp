package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CombinedNightServiceTest {

    @Mock
    private SleepHqClient client;

    private CombinedNightService service;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties(null, "cpap-1", "o2-1");
        service = new CombinedNightService(client, clinical);
    }

    @Test
    void combineForCalendarDate_overlaysO2SummariesWhenCpapMissingThem() {
        when(client.getMachineDateByDate(eq("cpap-1"), eq("2026-05-23")))
                .thenReturn("{\"data\":{\"id\":\"md-cpap\",\"type\":\"machine_date\",\"attributes\":{\"usage\":1,"
                        + "\"spo2_summary\":null},\"relationships\":{}}}");
        when(client.getMachineDateByDate(eq("o2-1"), eq("2026-05-23")))
                .thenReturn("{\"data\":{\"id\":\"md-o2\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"spo2_summary\":{\"av\":97},\"pulse_rate_summary\":{\"av\":56},"
                        + "\"movement_summary\":{\"av\":0.1}}}}");

        String json = service.combineForCalendarDate("2026-05-23", null, null);
        var root = JsonApi.parse(json);

        assertThat(root.path("data").path("id").asText()).isEqualTo("md-cpap");
        assertThat(root.path("data").path("type").asText()).isEqualTo("machine_date");
        assertThat(root.path("data").path("attributes").path("usage").asInt()).isEqualTo(1);
        assertThat(root.path("data").path("attributes").path("spo2_summary").path("av").asInt()).isEqualTo(97);
        assertThat(root.path("data").path("attributes").path("pulse_rate_summary").path("av").asInt()).isEqualTo(56);
        assertThat(root.path("data").path("attributes").path("movement_summary").path("av").asDouble()).isEqualTo(0.1);
    }

    @Test
    void combineForCalendarDate_o2Missing_cpapOnly() {
        when(client.getMachineDateByDate(eq("cpap-1"), eq("2026-05-20")))
                .thenReturn("{\"data\":{\"id\":\"9\",\"type\":\"machine_date\",\"attributes\":{\"usage\":3},"
                        + "\"relationships\":{}}}");
        when(client.getMachineDateByDate(eq("o2-1"), eq("2026-05-20")))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null,
                        "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        String json = service.combineForCalendarDate("2026-05-20", null, null);
        var root = JsonApi.parse(json);

        assertThat(root.path("data").path("id").asText()).isEqualTo("9");
        assertThat(root.path("data").path("attributes").path("usage").asInt()).isEqualTo(3);
        assertThat(root.path("data").path("attributes").path("spo2_summary").isMissingNode()).isTrue();
    }

    @Test
    void combineForCalendarDate_cpapMissing_throws() {
        when(client.getMachineDateByDate(eq("cpap-1"), eq("2026-05-19")))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null,
                        "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.combineForCalendarDate("2026-05-19", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No CPAP machine_date");
    }

    @Test
    void combineForCalendarDate_invalidDate_throws() {
        assertThatThrownBy(() -> service.combineForCalendarDate("05-20-2026", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void combineForCalendarDate_missingDefaults_throws() {
        CombinedNightService bare = new CombinedNightService(client,
                new ClinicalContextProperties(null, null, null));
        assertThatThrownBy(() -> bare.combineForCalendarDate("2026-05-20", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cpapMachineId");
    }

    @Test
    void combineForCalendarDate_cpapOnly_noO2Configured_doesNotFetchSecondMachine() {
        CombinedNightService cpapOnly = new CombinedNightService(client,
                new ClinicalContextProperties(null, "cpap-1", null));
        when(client.getMachineDateByDate(eq("cpap-1"), eq("2026-06-01")))
                .thenReturn("{\"data\":{\"id\":\"md-1\",\"type\":\"machine_date\",\"attributes\":{\"usage\":2},"
                        + "\"relationships\":{}}}");

        String json = cpapOnly.combineForCalendarDate("2026-06-01", null, null);
        var root = JsonApi.parse(json);

        assertThat(root.path("data").path("id").asText()).isEqualTo("md-1");
        verify(client).getMachineDateByDate(eq("cpap-1"), eq("2026-06-01"));
        verifyNoMoreInteractions(client);
    }

    @Test
    void mergeAttributes_cpapKeepsSpo2WhenPresent() {
        ObjectNode cpap = JsonApi.mapper().createObjectNode();
        cpap.putObject("spo2_summary").put("av", 95);
        ObjectNode o2 = JsonApi.mapper().createObjectNode();
        o2.putObject("spo2_summary").put("av", 99);

        ObjectNode merged = CombinedNightService.mergeAttributes(cpap, o2);
        assertThat(merged.path("spo2_summary").path("av").asInt()).isEqualTo(95);
    }
}
