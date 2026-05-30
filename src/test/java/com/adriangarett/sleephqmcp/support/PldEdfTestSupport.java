package com.adriangarett.sleephqmcp.support;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds a minimal valid EDF byte array for tests, mirroring ResMed PLD layout. Digital range is
 * fixed -2048..2047 mapped to each signal's physical min/max so callers write known physical values.
 */
public final class PldEdfTestSupport {

    private PldEdfTestSupport() {}

    public record Signal(String label, String unit, double physMin, double physMax, int samplesPerRecord) {}

    public static byte[] build(int nRecords, double recDuration, List<Signal> signals, List<List<Double>> physValues) {
        int ns = signals.size();
        int headerBytes = 256 + ns * 256;
        int recordBytes = 0;
        for (Signal s : signals) {
            recordBytes += s.samplesPerRecord() * 2;
        }
        byte[] edf = new byte[headerBytes + nRecords * recordBytes];
        java.util.Arrays.fill(edf, (byte) ' ');

        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, Integer.toString(headerBytes));
        writeString(edf, 236, Integer.toString(nRecords));
        writeString(edf, 244, Double.toString(recDuration));
        writeString(edf, 252, Integer.toString(ns));

        for (int i = 0; i < ns; i++) {
            Signal s = signals.get(i);
            writeString(edf, 256 + i * 16, s.label());
            writeString(edf, 256 + ns * 96 + i * 8, s.unit());
            writeString(edf, 256 + ns * 104 + i * 8, Double.toString(s.physMin()));
            writeString(edf, 256 + ns * 112 + i * 8, Double.toString(s.physMax()));
            writeString(edf, 256 + ns * 120 + i * 8, "-2048");
            writeString(edf, 256 + ns * 128 + i * 8, "2047");
            writeString(edf, 256 + ns * 216 + i * 8, Integer.toString(s.samplesPerRecord()));
        }

        int pos = headerBytes;
        for (int rec = 0; rec < nRecords; rec++) {
            for (int i = 0; i < ns; i++) {
                Signal s = signals.get(i);
                int spr = s.samplesPerRecord();
                for (int k = 0; k < spr; k++) {
                    double phys = physValues.get(i).get(rec * spr + k);
                    short raw = toRaw(phys, s.physMin(), s.physMax());
                    edf[pos++] = (byte) (raw & 0xFF);
                    edf[pos++] = (byte) ((raw >> 8) & 0xFF);
                }
            }
        }
        return edf;
    }

    private static short toRaw(double phys, double physMin, double physMax) {
        double frac = (phys - physMin) / (physMax - physMin);
        long raw = Math.round(frac * 4095.0 - 2048.0);
        return (short) Math.max(-2048, Math.min(2047, raw));
    }

    private static void writeString(byte[] buf, int offset, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
    }
}
