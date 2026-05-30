package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.NightSessionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiNightFileSourceTest {

    @Mock private SleepHqClient client;
    @Mock private RestClient s3RestClient;
    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private ApiNightFileSource source;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-1", "cpap-1", "o2-1", null);
        source = new ApiNightFileSource(client, s3RestClient, clinical);
    }

    @Test
    void cpapSessions_matchesDatalogFolderPath_andDownloadsLazily() {
        when(client.listTeamFiles(eq("team-1"), eq(1), eq(100))).thenReturn("""
                {
                  "data": [
                    { "id": "f1", "attributes": { "name": "20260418_015119_PLD.edf", "path": "./DATALOG/20260417/" } },
                    { "id": "f2", "attributes": { "name": "20260417_223000_BRP.edf", "path": "./DATALOG/20260417/" } },
                    { "id": "f3", "attributes": { "name": "20260415_PLD.edf", "path": "./DATALOG/20260415/" } }
                  ]
                }
                """);
        when(client.getImportFile("f1")).thenReturn("""
                { "data": { "attributes": { "name": "20260418_015119_PLD.edf",
                    "download_url": "https://s3.example/f1" } } }
                """);
        when(s3RestClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(URI.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(byte[].class)).thenReturn(new byte[]{9, 9});

        List<NightSessionFile> files = source.cpapSessions("2026-04-17");

        assertThat(files).extracting(NightSessionFile::name).containsExactly("20260418_015119_PLD.edf");
        assertThat(files.get(0).bytes().get()).containsExactly(9, 9); // download happens lazily on get()
    }
}
