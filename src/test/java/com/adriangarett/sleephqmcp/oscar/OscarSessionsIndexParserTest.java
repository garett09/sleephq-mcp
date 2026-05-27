package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OscarSessionsIndexParserTest {

    @Test
    void parse_validSessionsInfo_returnsEntries() throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/oscar/Sessions.info")) {
            bytes = in.readAllBytes();
        }
        List<OscarSessionsIndexParser.SessionEntry> entries = OscarSessionsIndexParser.parse(bytes);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).sessionId()).isEqualTo(0x69da9df0L);
        assertThat(entries.get(0).enabled()).isTrue();
        assertThat(entries.get(1).enabled()).isTrue();
    }
}
