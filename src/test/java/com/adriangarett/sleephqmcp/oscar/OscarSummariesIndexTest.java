package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OscarSummariesIndexTest {

    @Test
    void parse_handlesRealAttributeOrder_idEnabledEventsFirstLast() {
        String xml = """
                <sessions version="1" count="1" profile="adriansian" loader="ResMed" serial="123">
                 <session id="1776777120" enabled="1" events="1" first="1776777176000" last="1776800096000">
                  <channels>1101,1100,1103,1105,1106</channels>
                  <settings>e205,1020,1021,1200</settings>
                 </session>
                </sessions>
                """;
        OscarSummariesIndex index = OscarSummariesIndex.parse(xml);
        assertThat(index.sessions()).hasSize(1);
        OscarSessionIndexEntry s = index.sessions().get(0);
        assertThat(s.sessionId()).isEqualTo(1776777120L);
        assertThat(s.enabled()).isTrue();
        assertThat(s.channelIds()).contains(0x1103, 0x1105, 0x1106); // TidVol, MinVent, RespRate
    }

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
