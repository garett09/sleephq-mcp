package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OscarSummariesIndexTest {

    @Test
    void findPrimarySessionForDate_selectsLongestSessionOnDate() {
        String xml = """
                <sessions>
                 <session events="1" id="100" enabled="1" first="1000" last="2000">
                  <channels>1101</channels>
                  <settings>e210</settings>
                 </session>
                 <session events="1" id="200" enabled="1" first="1000" last="9000000">
                  <channels>1101,110e</channels>
                  <settings>e210</settings>
                 </session>
                </sessions>
                """;
        OscarSummariesIndex index = OscarSummariesIndex.parse(xml);
        LocalDate date = java.time.Instant.ofEpochMilli(1500).atZone(ZoneId.of("UTC")).toLocalDate();
        Optional<OscarSessionIndexEntry> found = index.findPrimarySessionForDate(date, ZoneId.of("UTC"));
        assertThat(found).isPresent();
        assertThat(found.get().sessionId()).isEqualTo(200L);
    }
}
