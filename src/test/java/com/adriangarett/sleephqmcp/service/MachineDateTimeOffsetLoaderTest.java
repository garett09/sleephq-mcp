package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineDateTimeOffsetLoaderTest {

    @Mock
    private SleepHqCacheFacade cacheFacade;

    private MachineDateTimeOffsetLoader loader;

    @BeforeEach
    void setUp() {
        loader = new MachineDateTimeOffsetLoader(cacheFacade, new ClinicalContextProperties("team-1", "cpap-9", "o2-1", null));
    }

    @Test
    void loadForCpapDate_validTimeOffset_returnsSeconds() {
        when(cacheFacade.getMachineDateByDate("cpap-9", "2026-05-20"))
                .thenReturn("""
                        {"data":{"id":"1","type":"machine_date","attributes":{"time_offset":1428,"date":"2026-05-20"}}}
                        """);

        assertThat(loader.loadForCpapDate("2026-05-20", null)).hasValue(1428);
    }

    @Test
    void loadForCpapDate_absurdTimeOffset_empty() {
        when(cacheFacade.getMachineDateByDate("cpap-9", "2026-05-20"))
                .thenReturn("""
                        {"data":{"id":"1","type":"machine_date","attributes":{"time_offset":3605430137}}}
                        """);

        assertThat(loader.loadForCpapDate("2026-05-20", null)).isEmpty();
    }

    @Test
    void loadForCpapDate_missingCpapMachineId_empty() {
        loader = new MachineDateTimeOffsetLoader(cacheFacade, new ClinicalContextProperties("team-1", null, null, null));

        assertThat(loader.loadForCpapDate("2026-05-20", null)).isEmpty();
    }
}
