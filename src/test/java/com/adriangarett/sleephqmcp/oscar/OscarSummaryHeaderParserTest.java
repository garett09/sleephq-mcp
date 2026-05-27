package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

class OscarSummaryHeaderParserTest {

    @Test
    void parse_validHeader_readsDurationAsSeconds() {
        ByteBuffer buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(OscarConstants.MAGIC);
        buf.putShort((short) 18);
        buf.putShort((short) 0);
        buf.putInt(0x5FCC391B);
        buf.putInt(0x69DA9DF0);
        buf.putLong(1_775_934_963_000L);
        buf.putLong(24_600L);
        OscarSummaryHeaderParser.SummaryHeader header =
                OscarSummaryHeaderParser.parse(buf.array());
        assertThat(header.sessionId()).isEqualTo(0x69DA9DF0L);
        assertThat(header.durationSeconds()).isEqualTo(24_600L);
        assertThat(header.startInstant().toEpochMilli()).isEqualTo(1_775_934_963_000L);
    }

    @Test
    void parse_whenLastFieldIsEndEpochMs_computesDurationSeconds() {
        ByteBuffer buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(OscarConstants.MAGIC);
        buf.putShort((short) 18);
        buf.putShort((short) 0);
        buf.putInt(0x5FCC391B);
        buf.putInt(0x6A0DB26C);
        long firstMs = 1_779_282_560_000L;
        long endMs = 1_779_314_300_000L;
        buf.putLong(firstMs);
        buf.putLong(endMs);
        OscarSummaryHeaderParser.SummaryHeader header =
                OscarSummaryHeaderParser.parse(buf.array());
        assertThat(header.durationSeconds()).isEqualTo(31_740L);
        assertThat(header.startInstant().toEpochMilli()).isEqualTo(firstMs);
    }

    @Test
    void resolveDurationSeconds_endEpochMsPattern_matchesResMedBackup() {
        assertThat(OscarSummaryHeaderParser.resolveDurationSeconds(1_779_282_560_000L, 1_779_314_300_000L))
                .isEqualTo(31_740L);
        assertThat(OscarSummaryHeaderParser.resolveDurationSeconds(1_775_934_963_000L, 24_600L))
                .isEqualTo(24_600L);
    }
}
