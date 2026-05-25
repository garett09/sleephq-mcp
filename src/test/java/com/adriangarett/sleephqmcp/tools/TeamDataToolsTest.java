package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.service.JournalLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamDataToolsTest {

    @Mock
    private SleepHqClient client;

    @Mock
    private JournalLookupService journalLookup;

    @Test
    void listSleepTests_blankTeamAndNoDefault_throws() {
        ClinicalContextProperties clinical = new ClinicalContextProperties(null, null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical, journalLookup);

        assertThatThrownBy(() -> tools.listSleepTests(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId");
    }

    @Test
    void listSleepTests_blankTeam_usesConfiguredDefault() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-99", null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical, journalLookup);
        when(client.listSleepTests(eq("team-99"), isNull(), isNull(), isNull())).thenReturn("{}");

        String result = tools.listSleepTests(null, null, null, null);

        assertThat(result).isEqualTo("{}");
        verify(client).listSleepTests("team-99", null, null, null);
    }

    @Test
    void listJournals_explicitTeamId_passesThrough() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("ignored", null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical, journalLookup);
        when(client.listJournals(eq("team-2"), isNull(), isNull())).thenReturn("[]");

        assertThat(tools.listJournals("team-2", null, null)).isEqualTo("[]");
        verify(client).listJournals("team-2", null, null);
    }

    @Test
    void listTeamFiles_noFilter_delegatesToClient() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-default", null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical, journalLookup);
        when(client.listTeamFiles("team-default", 1, 10)).thenReturn("{\"data\":[]}");

        String result = tools.listTeamFiles(null, null, 1, 10);

        assertThat(result).isEqualTo("{\"data\":[]}");
        verify(client).listTeamFiles("team-default", 1, 10);
    }

    @Test
    void listTeamFiles_withFilter_filtersMatchingFiles() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-default", null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical, journalLookup);

        String pageJson = """
                {
                  "data": [
                    { "type": "file", "id": "1", "attributes": { "name": "20260430_BRP.edf" } },
                    { "type": "file", "id": "2", "attributes": { "name": "other_file.txt" } }
                  ]
                }
                """;
        when(client.listTeamFiles("team-default", 1, 100)).thenReturn(pageJson);

        String result = tools.listTeamFiles(null, "BRP.edf", null, null);

        assertThat(result).contains("20260430_BRP.edf");
        assertThat(result).doesNotContain("other_file.txt");
        assertThat(result).contains("\"meta_total_matches\":1");
    }
}

