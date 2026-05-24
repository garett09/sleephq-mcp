package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
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

    @Test
    void listSleepTests_blankTeamAndNoDefault_throws() {
        ClinicalContextProperties clinical = new ClinicalContextProperties(null, null, null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical);

        assertThatThrownBy(() -> tools.listSleepTests(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId");
    }

    @Test
    void listSleepTests_blankTeam_usesConfiguredDefault() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("team-99", null, null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical);
        when(client.listSleepTests(eq("team-99"), isNull(), isNull(), isNull())).thenReturn("{}");

        String result = tools.listSleepTests(null, null, null, null);

        assertThat(result).isEqualTo("{}");
        verify(client).listSleepTests("team-99", null, null, null);
    }

    @Test
    void listJournals_explicitTeamId_passesThrough() {
        ClinicalContextProperties clinical = new ClinicalContextProperties("ignored", null, null, null);
        TeamDataTools tools = new TeamDataTools(client, clinical);
        when(client.listJournals(eq("team-2"), isNull(), isNull())).thenReturn("[]");

        assertThat(tools.listJournals("team-2", null, null)).isEqualTo("[]");
        verify(client).listJournals("team-2", null, null);
    }
}
