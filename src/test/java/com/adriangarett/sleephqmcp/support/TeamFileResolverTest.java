package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamFileResolverTest {

    @Mock
    private SleepHqClient client;

    @Test
    void resolveByDate_foundOnFirstPage_returnsFileId() {
        when(client.listTeamFiles(eq("team-1"), eq(1), eq(100))).thenReturn("""
                {
                  "data": [
                    {
                      "id": "file-99",
                      "attributes": { "name": "20260520_210920_BRP.edf" }
                    }
                  ]
                }
                """);

        String fileId = TeamFileResolver.resolveByDate(client, "team-1", "2026-05-20", "brp.edf");

        assertThat(fileId).isEqualTo("file-99");
        verify(client).listTeamFiles("team-1", 1, 100);
        verifyNoMoreInteractions(client);
    }

    @Test
    void resolveByDate_foundOnSecondPage_paginates() {
        StringBuilder entries = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                entries.append(',');
            }
            entries.append("""
                    { "id": "other-%d", "attributes": { "name": "unrelated.edf" } }
                    """.formatted(i));
        }
        when(client.listTeamFiles(eq("team-1"), eq(1), eq(100))).thenReturn("""
                {
                  "data": [
                """ + entries + """
                  ]
                }
                """);
        when(client.listTeamFiles(eq("team-1"), eq(2), eq(100))).thenReturn("""
                {
                  "data": [
                    { "id": "file-2", "attributes": { "name": "20260521_EVE.edf" } }
                  ]
                }
                """);

        String fileId = TeamFileResolver.resolveByDate(client, "team-1", "2026-05-21", "eve.edf");

        assertThat(fileId).isEqualTo("file-2");
        verify(client).listTeamFiles("team-1", 1, 100);
        verify(client).listTeamFiles("team-1", 2, 100);
    }

    @Test
    void resolveByDate_notFound_throws() {
        when(client.listTeamFiles(eq("team-1"), eq(1), eq(100))).thenReturn("""
                { "data": [] }
                """);

        assertThatThrownBy(() -> TeamFileResolver.resolveByDate(client, "team-1", "2026-05-20", "brp.edf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20260520")
                .hasMessageContaining("team-1");
    }

    @Test
    void resolveByDate_invalidDateFormat_throws() {
        assertThatThrownBy(() -> TeamFileResolver.resolveByDate(client, "team-1", "2026-5-20", "brp.edf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }
}
