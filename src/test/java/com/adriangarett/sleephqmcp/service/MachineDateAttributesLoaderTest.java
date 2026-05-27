package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineDateAttributesLoaderTest {

    @Mock
    private SleepHqClient client;

    private MachineDateAttributesLoader loader;

    @BeforeEach
    void setUp() {
        loader = new MachineDateAttributesLoader(client,
                new ClinicalContextProperties("team-1", "cpap-9", "o2-1", null));
    }

    @Test
    void loadOrNull_validJsonApi_returnsAttributesNode() {
        when(client.getMachineDateByDate("cpap-9", "2026-05-20"))
                .thenReturn("""
                        {"data":{"id":"1","type":"machine_date","attributes":{"ahi_summary":{"av":2.3,"oa":0.1,"ca":0.0,"h":2.2},"date":"2026-05-20"}}}
                        """);

        JsonNode attrs = loader.loadOrNull("2026-05-20");

        assertThat(attrs).isNotNull();
        assertThat(attrs.path("ahi_summary").path("av").asDouble()).isEqualTo(2.3);
    }

    @Test
    void loadOrNull_restClientException_returnsNull() {
        when(client.getMachineDateByDate("cpap-9", "2026-05-20"))
                .thenThrow(new RestClientException("500 Internal Server Error"));

        assertThat(loader.loadOrNull("2026-05-20")).isNull();
    }

    @Test
    void loadOrNull_missingCpapMachineId_returnsNull() {
        loader = new MachineDateAttributesLoader(client,
                new ClinicalContextProperties("team-1", null, null, null));

        assertThat(loader.loadOrNull("2026-05-20")).isNull();
    }

    @Test
    void loadOrNull_noDataInResponse_returnsNull() {
        when(client.getMachineDateByDate("cpap-9", "2026-05-20"))
                .thenReturn("""
                        {"data":null}
                        """);

        assertThat(loader.loadOrNull("2026-05-20")).isNull();
    }
}
