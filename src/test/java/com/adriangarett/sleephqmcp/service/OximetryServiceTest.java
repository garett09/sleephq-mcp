package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.ViatomTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OximetryServiceTest {

    @Mock
    private SleepHqClient sleepHqClient;

    @Mock
    private SleepHqCacheFacade cacheFacade;

    private OximetryService oximetryService;

    @BeforeEach
    void setUp() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-123", "cpap-1", "81007", null);
        oximetryService = new OximetryService(sleepHqClient, cacheFacade, clinical);
    }

    @Test
    void getOximetry_parsesViatomBinary() {
        when(cacheFacade.getImportFile("316855731")).thenReturn("""
                {
                  "data": {
                    "attributes": {
                      "name": "20260525011013-1721",
                      "download_url": "https://s3.example.com/o2?sig=1"
                    }
                  }
                }
                """);
        when(cacheFacade.downloadEdf(any(URI.class), org.mockito.ArgumentMatchers.eq("316855731")))
                .thenReturn(ViatomTestFixtures.vld3Session());

        String json = oximetryService.getOximetry("316855731", 3600);
        assertThat(json).contains("\"source\":\"viatom_vld3\"");
        assertThat(json).contains("\"spo2\":98");
        assertThat(json).contains("\"pulse_bpm\":72");
    }
}
