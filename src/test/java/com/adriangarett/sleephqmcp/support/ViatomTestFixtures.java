package com.adriangarett.sleephqmcp.support;

/**
 * Synthetic Viatom VLD3 session bytes for unit tests (4 records × 4 s = 16 s duration).
 */
public final class ViatomTestFixtures {

    private ViatomTestFixtures() {}

    /**
     * O2Ring S layout (OSCAR sig 0x0301): 10-byte header, 3-byte records, 36-byte trailer with record count.
     */
    public static byte[] o2RingSSession() {
        int recordCount = 4;
        byte[] data = new byte[10 + recordCount * 3 + 36];
        data[0] = 0x01;
        data[1] = 0x03;
        int pos = 10;
        writeO2RingRecord(data, pos, 98, 72, 0);
        writeO2RingRecord(data, pos + 3, 97, 71, 1);
        writeO2RingRecord(data, pos + 6, 96, 70, 0);
        writeO2RingRecord(data, pos + 9, 95, 69, 2);
        int trailer = data.length - 36;
        data[trailer] = (byte) recordCount;
        data[trailer + 1] = 0;
        return data;
    }

    public static byte[] vld3Session() {
        byte[] data = new byte[40 + 4 * 5];
        data[0] = 3;
        data[1] = 0;
        data[2] = (byte) 0xEA;
        data[3] = 0x07;
        data[4] = 5;
        data[5] = 25;
        data[6] = 1;
        data[7] = 10;
        data[8] = 13;
        data[18] = 16;
        data[19] = 0;

        int pos = 40;
        writeRecord(data, pos, 98, 72, 0, 0, 0);
        writeRecord(data, pos + 5, 97, 71, 0, 1, 0);
        writeRecord(data, pos + 10, 96, 70, 0, 0, 0);
        writeRecord(data, pos + 15, 95, 69, 0, 2, 0);
        return data;
    }

    private static void writeO2RingRecord(byte[] data, int pos, int spo2, int pulse, int motion) {
        data[pos] = (byte) spo2;
        data[pos + 1] = (byte) pulse;
        data[pos + 2] = (byte) motion;
    }

    private static void writeRecord(byte[] data, int pos, int spo2, int pulse, int invalid, int motion, int vibration) {
        data[pos] = (byte) spo2;
        data[pos + 1] = (byte) pulse;
        data[pos + 2] = (byte) invalid;
        data[pos + 3] = (byte) motion;
        data[pos + 4] = (byte) vibration;
    }
}
