package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.OximetryResult;
import com.adriangarett.sleephqmcp.domain.OximetrySample;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Viatom / Wellue O2 Ring binary session files (classic VLD3 and O2Ring S / 0x0301).
 */
public final class ViatomSessionParser {

    private static final int VLD3_HEADER_END = 40;
    private static final int VLD3_RECORD_SIZE = 5;
    private static final int O2RING_S_SIGNATURE = 0x0301;
    private static final int O2RING_S_HEADER_SKIP = 10;
    private static final int O2RING_S_TRAILER_BYTES = 36;
    private static final int O2RING_S_RECORD_SIZE = 3;
    private static final double O2RING_S_INTERVAL_SECONDS = 1.0;
    private static final int MAX_O2RING_S_RECORDS = 36_000;
    private static final int INVALID_BYTE = 0xFF;
    private static final Pattern FILENAME_DATETIME =
            Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})");

    private ViatomSessionParser() {}

    public static OximetryResult parse(byte[] data, String filename, int maxSeconds) {
        if (data.length < 4) {
            throw formatError(data, "file too short (" + data.length + " bytes)");
        }

        int signature = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
        if (signature == O2RING_S_SIGNATURE) {
            return parseO2RingS(data, filename, maxSeconds);
        }
        if (signature == 2 || signature == 3) {
            return parseVld3(data, filename, maxSeconds);
        }
        throw formatError(data, "unsupported Viatom signature " + signature);
    }

    private static OximetryResult parseVld3(byte[] data, String filename, int maxSeconds) {
        if (data.length < VLD3_HEADER_END + VLD3_RECORD_SIZE) {
            throw formatError(data, "file too short for VLD3 (" + data.length + " bytes)");
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int year = buf.getShort(2) & 0xFFFF;
        int month = buf.get(4) & 0xFF;
        int day = buf.get(5) & 0xFF;
        int hour = buf.get(6) & 0xFF;
        int minute = buf.get(7) & 0xFF;
        int second = buf.get(8) & 0xFF;
        int durationSeconds = buf.getShort(18) & 0xFFFF;

        LocalDateTime start = resolveStartDatetime(filename, year, month, day, hour, minute, second);
        int recordCount = (data.length - VLD3_HEADER_END) / VLD3_RECORD_SIZE;
        if (recordCount <= 0) {
            throw formatError(data, "no measurement records after VLD3 header");
        }

        double interval = durationSeconds > 0 ? (double) durationSeconds / recordCount : 4.0;
        List<OximetrySample> samples = readVld3Records(data, recordCount, interval, maxSeconds);

        double actualDuration = samples.isEmpty() ? 0 : samples.get(samples.size() - 1).elapsedSeconds() + interval;
        if (durationSeconds <= 0) {
            durationSeconds = (int) Math.ceil(actualDuration);
        }

        return new OximetryResult(
                filename != null ? filename : "",
                start.toString(),
                durationSeconds,
                interval,
                "viatom_vld3",
                samples
        );
    }

    private static OximetryResult parseO2RingS(byte[] data, String filename, int maxSeconds) {
        int minSize = O2RING_S_HEADER_SKIP + O2RING_S_RECORD_SIZE + O2RING_S_TRAILER_BYTES;
        if (data.length < minSize) {
            throw formatError(data, "file too short for O2Ring S (" + data.length + " bytes)");
        }

        int trailerOffset = data.length - O2RING_S_TRAILER_BYTES;
        int recordCount = (data[trailerOffset] & 0xFF) | ((data[trailerOffset + 1] & 0xFF) << 8);
        if (recordCount <= 0 || recordCount > MAX_O2RING_S_RECORDS) {
            throw formatError(data, "invalid O2Ring S record count " + recordCount);
        }

        int maxBySize = (trailerOffset - O2RING_S_HEADER_SKIP) / O2RING_S_RECORD_SIZE;
        if (maxBySize <= 0) {
            throw formatError(data, "no O2Ring S records between header and trailer");
        }
        if (recordCount > maxBySize) {
            recordCount = maxBySize;
        }

        LocalDateTime start = resolveStartDatetime(filename, 0, 0, 0, 0, 0, 0);
        List<OximetrySample> samples = readO2RingSRecords(data, recordCount, maxSeconds);

        // Report duration from the samples actually decoded — maxSeconds may have truncated them, so the
        // pre-trim record count would over-report the session span returned to the caller.
        double actualDuration = samples.isEmpty() ? 0.0
                : samples.get(samples.size() - 1).elapsedSeconds() + O2RING_S_INTERVAL_SECONDS;

        return new OximetryResult(
                filename != null ? filename : "",
                start.toString(),
                actualDuration,
                O2RING_S_INTERVAL_SECONDS,
                "viatom_o2ring_s",
                samples
        );
    }

    private static List<OximetrySample> readVld3Records(byte[] data, int recordCount, double interval, int maxSeconds) {
        List<OximetrySample> samples = new ArrayList<>(recordCount);
        int pos = VLD3_HEADER_END;
        for (int i = 0; i < recordCount; i++) {
            double elapsed = i * interval;
            if (maxSeconds > 0 && elapsed >= maxSeconds) {
                break;
            }
            int spo2Raw = data[pos] & 0xFF;
            int pulseRaw = data[pos + 1] & 0xFF;
            int invalidFlag = data[pos + 2] & 0xFF;
            int motion = data[pos + 3] & 0xFF;
            pos += VLD3_RECORD_SIZE;

            boolean invalid = invalidFlag != 0 || spo2Raw == INVALID_BYTE || pulseRaw == INVALID_BYTE;
            int spo2 = spo2Raw == INVALID_BYTE ? -1 : spo2Raw;
            int pulse = pulseRaw == INVALID_BYTE ? -1 : pulseRaw;
            samples.add(new OximetrySample(elapsed, spo2, pulse, motion, invalid));
        }
        return samples;
    }

    private static List<OximetrySample> readO2RingSRecords(byte[] data, int recordCount, int maxSeconds) {
        List<OximetrySample> samples = new ArrayList<>(recordCount);
        int pos = O2RING_S_HEADER_SKIP;
        for (int i = 0; i < recordCount; i++) {
            double elapsed = i * O2RING_S_INTERVAL_SECONDS;
            if (maxSeconds > 0 && elapsed >= maxSeconds) {
                break;
            }
            int spo2Raw = data[pos] & 0xFF;
            int pulseRaw = data[pos + 1] & 0xFF;
            int motion = data[pos + 2] & 0xFF;
            pos += O2RING_S_RECORD_SIZE;

            boolean invalid = spo2Raw == INVALID_BYTE || pulseRaw == INVALID_BYTE;
            int spo2 = invalid ? -1 : spo2Raw;
            int pulse = invalid ? -1 : pulseRaw;
            samples.add(new OximetrySample(elapsed, spo2, pulse, motion, invalid));
        }
        return samples;
    }

    private static LocalDateTime resolveStartDatetime(String filename, int year, int month, int day,
                                                      int hour, int minute, int second) {
        if (year >= 2000 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
            return LocalDateTime.of(year, month, day, hour, minute, second);
        }
        return startFromFilename(filename);
    }

    private static LocalDateTime startFromFilename(String filename) {
        if (filename != null) {
            Matcher m = FILENAME_DATETIME.matcher(filename);
            if (m.find()) {
                return LocalDateTime.of(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4)),
                        Integer.parseInt(m.group(5)),
                        Integer.parseInt(m.group(6))
                );
            }
        }
        throw new IllegalArgumentException("Could not determine session start time from header or filename");
    }

    private static IllegalArgumentException formatError(byte[] data, String detail) {
        String hex = hexPrefix(data, 16);
        return new IllegalArgumentException("Not a Viatom VLD session: " + detail + " (header hex: " + hex + ")");
    }

    static String hexPrefix(byte[] data, int n) {
        int len = Math.min(n, data.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", data[i]));
        }
        return sb.toString();
    }
}
