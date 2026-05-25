package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.support.JsonApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NightServiceTest {

    @Mock
    private SleepHqClient client;

    @Mock
    private JournalLookupService journalLookup;

    private NightService service;

    @BeforeEach
    void setUp() {
        service = new NightService(client, journalLookup);
    }

    @Test
    void getNightStats_attachesJournalByMachineDateCalendarDate() {
        when(client.getMachineDate(eq("md-1")))
                .thenReturn("{\"data\":{\"id\":\"md-1\",\"type\":\"machine_date\",\"attributes\":{\"date\":\"2026-05-23\"}}}");
        var attrs = JsonApi.mapper().createObjectNode();
        attrs.put("date", "2026-05-23");
        attrs.put("step_count", 9000);
        when(journalLookup.findAttributesByDate(isNull(), eq("2026-05-23"))).thenReturn(Optional.of(attrs));

        String json = service.getNightStats("md-1");
        assertThat(JsonApi.parse(json).path("journal").path("step_count").asInt()).isEqualTo(9000);
    }

    @Test
    void getNightStats_noJournal_omitsSibling() {
        when(client.getMachineDate(eq("md-2")))
                .thenReturn("{\"data\":{\"id\":\"md-2\",\"type\":\"machine_date\",\"attributes\":{\"date\":\"2026-05-20\"}}}");
        when(journalLookup.findAttributesByDate(isNull(), eq("2026-05-20"))).thenReturn(Optional.empty());

        String json = service.getNightStats("md-2");
        assertThat(JsonApi.parse(json).path("journal").isMissingNode()).isTrue();
    }
}
