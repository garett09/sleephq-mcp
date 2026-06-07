package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OscarSummaryParserTest {

    @Test
    void readAvailableChannelsFromTail_scansOnlyLast256Bytes() {
        int tailOffset = 400;
        ByteBuffer buf = ByteBuffer.allocate(tailOffset + 16).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(tailOffset);
        buf.putInt(3);
        buf.putInt(0x1103);
        buf.putInt(0x1106);
        buf.putInt(0x1105);
        // Decoy channel list at byte 32 — outside last-256 window, must not win
        buf.position(32);
        buf.putInt(3);
        buf.putInt(0x1100);
        buf.putInt(0x1101);
        buf.putInt(0x1102);

        List<Integer> ids = OscarSummaryParser.readAvailableChannelsFromTail(buf.array());
        assertThat(ids).containsExactly(0x1103, 0x1106, 0x1105);
    }

    @Test
    void readAvailableChannelsFromTail_findsChannelList() {
        ByteBuffer buf = ByteBuffer.allocate(288).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(256);
        buf.putInt(3);
        buf.putInt(0x1103);
        buf.putInt(0x1106);
        buf.putInt(0x1105);
        List<Integer> ids = OscarSummaryParser.readAvailableChannelsFromTail(buf.array());
        assertThat(ids).containsExactly(0x1103, 0x1106, 0x1105);
    }

    @Test
    void parseHeaderOnly_returnsSessionMetadata() {
        ByteBuffer buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(OscarConstants.MAGIC);
        buf.putShort((short) 18);
        buf.putShort((short) 0);
        buf.putInt(0x5FCC391B);
        buf.putInt(0x6A0DB26C);
        buf.putLong(1_779_282_540_000L);
        buf.putLong(28_800L);
        var session = OscarSummaryParser.parseHeaderOnly(buf.array(), LocalDate.parse("2026-05-20"),
                ZoneId.of("UTC"), List.of(0x1103, 0x1106));
        assertThat(session.sessionId()).isEqualTo(0x6A0DB26CL);
        assertThat(session.durationSeconds()).isEqualTo(28_800L);
        // TODO(Task 11): availableChannelIds() removed in OSCAR 2.0; now uses availableChannelCodes()
        assertThat(session.availableChannelCodes()).isNotNull();
    }
}
