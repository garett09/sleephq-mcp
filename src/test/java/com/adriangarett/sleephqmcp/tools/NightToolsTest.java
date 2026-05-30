package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.service.CombinedNightService;
import com.adriangarett.sleephqmcp.service.NightService;
import com.adriangarett.sleephqmcp.service.SleepHqNightSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NightToolsTest {

    @Mock private NightService nightService;
    @Mock private CombinedNightService combinedNightService;
    @Mock private SleepHqNightSummaryService nightSummaryService;

    @Test
    void getSleephqNight_delegatesToService() {
        when(nightSummaryService.getNightSummary("2026-05-28", null, null))
                .thenReturn("{\"source\":\"sleephq\"}");
        NightTools tools = new NightTools(nightService, combinedNightService, nightSummaryService);
        assertThat(tools.getSleephqNight("2026-05-28", null, null)).contains("\"source\":\"sleephq\"");
    }

    @Test
    void getSleephqNight_onError_returnsSafeErrorJson() {
        when(nightSummaryService.getNightSummary("2026-05-28", null, null))
                .thenThrow(new IllegalArgumentException("no_sleephq_data_for_date: none"));
        NightTools tools = new NightTools(nightService, combinedNightService, nightSummaryService);
        assertThat(tools.getSleephqNight("2026-05-28", null, null)).contains("no_sleephq_data_for_date");
    }
}
