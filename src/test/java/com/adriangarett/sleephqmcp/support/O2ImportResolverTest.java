package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class O2ImportResolverTest {

    @Mock
    private SleepHqClient client;

    @Test
    void resolveFileIdByDate_matchesO2MachineAndDate() {
        when(client.listImports("team-1", 1, 25)).thenReturn("""
                {
                  "data": [
                    {
                      "id": "imp-cpap",
                      "attributes": {
                        "machine_id": "81272",
                        "file_size": 100000000,
                        "name": "CPAP upload",
                        "created_at": "2026-05-25 10:00:00 +0800"
                      }
                    },
                    {
                      "id": "imp-o2",
                      "attributes": {
                        "machine_id": "81007",
                        "file_size": 74000,
                        "name": "Data from Monday",
                        "created_at": "2026-05-25 08:00:00 +0800"
                      }
                    }
                  ]
                }
                """);
        when(client.listImportFiles("imp-o2", 1, 10)).thenReturn("""
                {
                  "data": [
                    {
                      "id": "file-o2",
                      "attributes": { "name": "20260525011013-1721" }
                    }
                  ]
                }
                """);

        String fileId = O2ImportResolver.resolveFileIdByDate(client, "team-1", "81007", "2026-05-25");
        assertThat(fileId).isEqualTo("file-o2");
    }
}
