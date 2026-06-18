package com.adriangarett.sleephqmcp.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Shared EDF binary header reading used by {@link EdfParser} and {@link EdfAnnotationParser}.
 */
public final class EdfBinarySupport {

    public static final String ANNOTATIONS_LABEL = "EDF Annotations";

    private EdfBinarySupport() {}

    /**
     * Strict header read for waveform EDF files (BRP, PLD, etc.).
     */
    public static EdfHeader readHeader(byte[] edf) {
        return readHeader(edf, false);
    }

    /**
     * Header read for annotation / event EDF+ files (EVE.edf). Allows {@code record duration = 0}
     * per EDF+ when only an {@code EDF Annotations} channel is present.
     */
    public static EdfHeader readHeaderForAnnotations(byte[] edf) {
        return readHeader(edf, true);
    }

    private static EdfHeader readHeader(byte[] edf, boolean allowZeroRecordDuration) {
        if (edf.length < 256) {
            throw new IllegalArgumentException("Not an EDF file: too short (" + edf.length + " bytes)");
        }
        String version = readAscii(edf, 0, 8);
        if (!version.equals("0")) {
            throw new IllegalArgumentException("Not an EDF file: version field is '" + version + "'");
        }

        String startDate = readAscii(edf, 168, 8);
        String startTime = readAscii(edf, 176, 8);
        int headerBytes = parseIntField(edf, 184, 8);
        int nRecords = parseIntField(edf, 236, 8);
        double recDuration = parseDoubleField(edf, 244, 8);
        int ns = parseIntField(edf, 252, 4);

        if (recDuration < 0) {
            throw new IllegalArgumentException("Not an EDF file: invalid record duration " + recDuration);
        }
        if (recDuration == 0 && !allowZeroRecordDuration) {
            throw new IllegalArgumentException("Not an EDF file: invalid record duration 0.0");
        }
        if (edf.length < headerBytes) {
            throw new IllegalArgumentException(
                    "Not an EDF file: declared header " + headerBytes + " bytes but file is only " + edf.length);
        }

        String[] labels = readAsciiBlock(edf, 256, ns, 16);
        int[] samplesPerRec = parseIntBlock(edf, 256 + ns * 216, ns, 8);
        if (recDuration == 0 && annotationChannelIndex(labels) < 0) {
            throw new IllegalArgumentException(
                    "Not an EDF file: record duration 0.0 requires an EDF Annotations channel");
        }
        LocalDateTime startDatetime = parseEdfDatetime(startDate, startTime);

        return new EdfHeader(startDatetime, headerBytes, nRecords, recDuration, ns, labels, samplesPerRec);
    }

    private static int annotationChannelIndex(String[] labels) {
        for (int i = 0; i < labels.length; i++) {
            if (ANNOTATIONS_LABEL.equals(labels[i])) {
                return i;
            }
        }
        return -1;
    }

    public static int annotationChannelIndex(EdfHeader header) {
        for (int i = 0; i < header.signalCount(); i++) {
            if (ANNOTATIONS_LABEL.equals(header.labels()[i])) {
                return i;
            }
        }
        return -1;
    }

    public static int recordSizeBytes(EdfHeader header) {
        int size = 0;
        for (int s : header.samplesPerRecord()) {
            size += s * 2;
        }
        return size;
    }

    public static byte[] extractAnnotationBytes(byte[] edf, EdfHeader header) {
        int annIdx = annotationChannelIndex(header);
        if (annIdx < 0) {
            throw new IllegalArgumentException("No EDF Annotations channel in file");
        }
        int recordSize = recordSizeBytes(header);
        int annBytesPerRecord = header.samplesPerRecord()[annIdx] * 2;
        int prefixBytes = 0;
        for (int i = 0; i < annIdx; i++) {
            prefixBytes += header.samplesPerRecord()[i] * 2;
        }

        // EVE.edf often has record duration 0 and nRecords 0 or 1 — events still live in TAL bytes.
        int nRecords = resolveRecordCount(edf, header, recordSize);
        byte[] all = new byte[nRecords * annBytesPerRecord];
        int written = 0;
        for (int rec = 0; rec < nRecords; rec++) {
            int recStart = header.headerBytes() + rec * recordSize + prefixBytes;
            if (recStart + annBytesPerRecord > edf.length) {
                break;
            }
            System.arraycopy(edf, recStart, all, written, annBytesPerRecord);
            written += annBytesPerRecord;
        }
        if (written < all.length) {
            byte[] trimmed = new byte[written];
            System.arraycopy(all, 0, trimmed, 0, written);
            return trimmed;
        }
        return all;
    }

    public static double[] parseDoubleBlock(byte[] buf, int offset, int ns, int fieldLen) {
        double[] result = new double[ns];
        for (int i = 0; i < ns; i++) {
            result[i] = parseDoubleField(buf, offset + i * fieldLen, fieldLen);
        }
        return result;
    }

    private static int resolveRecordCount(byte[] edf, EdfHeader header, int recordSize) {
        if (recordSize <= 0) {
            return 0;
        }
        int declared = header.nRecords();
        if (declared > 0) {
            return declared;
        }
        int estimated = estimateRecordCount(edf, header, recordSize);
        return Math.max(1, estimated);
    }

    private static int estimateRecordCount(byte[] edf, EdfHeader header, int recordSize) {
        int dataBytes = edf.length - header.headerBytes();
        if (recordSize <= 0) {
            return 0;
        }
        return dataBytes / recordSize;
    }

    public static String readAscii(byte[] buf, int offset, int len) {
        return new String(buf, offset, len, StandardCharsets.US_ASCII).trim();
    }

    public static String[] readAsciiBlock(byte[] buf, int offset, int ns, int fieldLen) {
        String[] result = new String[ns];
        for (int i = 0; i < ns; i++) {
            result[i] = readAscii(buf, offset + i * fieldLen, fieldLen);
        }
        return result;
    }

    public static int[] parseIntBlock(byte[] buf, int offset, int ns, int fieldLen) {
        int[] result = new int[ns];
        for (int i = 0; i < ns; i++) {
            result[i] = parseIntField(buf, offset + i * fieldLen, fieldLen);
        }
        return result;
    }

    public static int parseIntField(byte[] buf, int offset, int len) {
        String s = readAscii(buf, offset, len);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "EDF parse error: expected int at offset " + offset + ", got '" + s + "'");
        }
    }

    public static double parseDoubleField(byte[] buf, int offset, int len) {
        String s = readAscii(buf, offset, len);
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "EDF parse error: expected double at offset " + offset + ", got '" + s + "'");
        }
    }

    public static LocalDateTime parseEdfDatetime(String dateStr, String timeStr) {
        String[] dp = dateStr.split("\\.");
        String[] tp = timeStr.split("\\.");
        if (dp.length != 3 || tp.length != 3) {
            throw new IllegalArgumentException(
                    "EDF date/time format invalid: '" + dateStr + "' / '" + timeStr + "'");
        }
        int day = Integer.parseInt(dp[0]);
        int month = Integer.parseInt(dp[1]);
        int yy = Integer.parseInt(dp[2]);
        int year = yy <= 84 ? 2000 + yy : 1900 + yy;
        int hour = Integer.parseInt(tp[0]);
        int min = Integer.parseInt(tp[1]);
        int sec = Integer.parseInt(tp[2]);
        return LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, min, sec));
    }

    public static double scale(short digital, int digMin, int digMax, double physMin, double physMax) {
        // A flat channel (digMax==digMin) is legitimate EDF and reads as physMin. An INVERTED range
        // (digMax<digMin) or non-finite physical bounds means a corrupt header: scaling it would emit a
        // sign-flipped/garbage value that looks real. Degrade to the physical floor (a visibly constant,
        // implausible line) rather than fabricate plausible-but-wrong samples.
        if (digMax <= digMin || !Double.isFinite(physMin) || !Double.isFinite(physMax)) {
            return Double.isFinite(physMin) ? physMin : 0.0;
        }
        return physMin + ((double) (digital - digMin)) * (physMax - physMin) / (digMax - digMin);
    }

    public static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
