package com.adriangarett.sleephqmcp.oscar;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class OscarBinaryReader {

    private final ByteBuffer buffer;

    public OscarBinaryReader(byte[] bytes) {
        this.buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    public int position() {
        return buffer.position();
    }

    public void position(int pos) {
        buffer.position(pos);
    }

    public int remaining() {
        return buffer.remaining();
    }

    public int peekByte() {
        return buffer.get(buffer.position()) & 0xFF;
    }

    public int readUInt32() {
        return buffer.getInt();
    }

    public int readUInt16() {
        return buffer.getShort() & 0xFFFF;
    }

    public int readUInt8() {
        return buffer.get() & 0xFF;
    }

    public long readInt64() {
        return buffer.getLong();
    }

    public double readDouble() {
        return buffer.getDouble();
    }

    public void expectMagic() {
        int magic = readUInt32();
        if (magic != OscarConstants.MAGIC) {
            throw new IllegalArgumentException(
                    "Invalid OSCAR magic: expected 0x" + Integer.toHexString(OscarConstants.MAGIC)
                            + " got 0x" + Integer.toHexString(magic));
        }
    }
}
