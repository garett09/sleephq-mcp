package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JournalSleepStagesParserTest {

    @Test
    void tryParse_jsonObject_returnsNode() {
        var parsed = JournalSleepStagesParser.tryParse("{\"stages\":[{\"stage\":\"deep\",\"minutes\":90}]}");
        assertThat(parsed).isNotNull();
        assertThat(parsed.path("stages").isArray()).isTrue();
    }

    @Test
    void tryParse_plainText_returnsNull() {
        assertThat(JournalSleepStagesParser.tryParse("Light sleep most of night.")).isNull();
    }

    @Test
    void tryParse_blank_returnsNull() {
        assertThat(JournalSleepStagesParser.tryParse("  ")).isNull();
    }
}
