package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceContextServiceTest {

    @Mock
    private SleepHqClient client;

    private DeviceContextService service;

    @BeforeEach
    void setUp() {
        service = new DeviceContextService(client, new ClinicalContextProperties("team-1", "cpap-1", "o2-1", null));
    }

    @Test
    void deviceContextJson_includesSettingsMachinesAndMasks() {
        when(client.listMachineDates(eq("cpap-1"), eq("desc"), eq(1), eq(1)))
                .thenReturn("{\"data\":[{\"id\":\"99\",\"type\":\"machine_date\","
                        + "\"attributes\":{\"date\":\"2026-05-24\"}}]}");
        when(client.getMachineDateByDate(eq("cpap-1"), eq("2026-05-24")))
                .thenReturn("{\"data\":{\"id\":\"99\",\"type\":\"machine_date\",\"attributes\":{"
                        + "\"date\":\"2026-05-24\","
                        + "\"machine_settings\":{\"mode\":\"CPAP\",\"pressure\":10.6,\"epr\":\"Off\",\"ramp\":\"Off\"}"
                        + "},\"relationships\":{}}}");
        when(client.getMachine(eq("cpap-1")))
                .thenReturn("{\"data\":{\"id\":\"cpap-1\",\"type\":\"machine\",\"attributes\":{\"brand\":\"ResMed\"}}}");
        when(client.getMachine(eq("o2-1")))
                .thenReturn("{\"data\":{\"id\":\"o2-1\",\"type\":\"machine\",\"attributes\":{\"brand\":\"Viatom\"}}}");
        when(client.listMasks(eq("team-1"), eq(1), eq(50)))
                .thenReturn("{\"data\":[{\"id\":\"m1\",\"type\":\"mask\",\"attributes\":{\"name\":\"AirFit F40\"}}]}");

        String json = service.deviceContextJson(null);
        var root = JsonApi.parse(json);

        assertThat(root.path("therapy_date").asText()).isEqualTo("2026-05-24");
        assertThat(root.path("machine_settings").path("pressure").asDouble()).isEqualTo(10.6);
        assertThat(root.path("cpap_machine").path("attributes").path("brand").asText()).isEqualTo("ResMed");
        assertThat(root.path("o2_machine").path("attributes").path("brand").asText()).isEqualTo("Viatom");
        assertThat(root.path("registered_masks").isArray()).isTrue();
        assertThat(root.path("registered_masks")).hasSize(1);
        assertThat(root.path("configured").path("cpap_machine_id").asText()).isEqualTo("cpap-1");
    }

    @Test
    void deviceContextJson_emptyList_throws() {
        when(client.listMachineDates(eq("cpap-1"), eq("desc"), eq(1), eq(1)))
                .thenReturn("{\"data\":[]}");

        assertThatThrownBy(() -> service.deviceContextJson(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No machine_dates");
    }
}
