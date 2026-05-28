package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;

class OscarSummaryParserBoundsTest {

    @Test
    void parse_truncatedHash_doesNotThrow() {
        ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(OscarConstants.MAGIC);
        buf.putShort((short) 18);
        buf.putShort((short) 0);
        buf.putInt(1);
        buf.putInt(2);
        buf.putLong(1_779_282_540_000L);
        buf.putLong(28_800L);
        buf.position(32);
        buf.putInt(999); // bogus entry count — old code seeks past buffer

        assertThatCode(() -> OscarSummaryParser.parse(buf.array(), LocalDate.parse("2026-05-20"),
                ZoneId.of("UTC"))).doesNotThrowAnyException();
    }
}
