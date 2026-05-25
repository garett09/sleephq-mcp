package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses EDF (European Data Format) binary files into {@link WaveformResult}.
 * Implements the EDF spec (https://www.edfplus.info/specs/edf.html) with no external dependencies.
 * The returned result has {@code filename=null} — the caller must set it from the metadata source.
 */
public final class EdfParser {

    private EdfParser() {}

    public static WaveformResult parse(byte[] edf, int startSeconds, int maxSeconds) {
        if (edf.length < 256) {
            throw new IllegalArgumentException("Not an EDF file: too short (" + edf.length + " bytes)");
        }

        // --- global header (256 bytes ASCII) ---
        String version = readAscii(edf, 0, 8);
        if (!version.equals("0")) {
            throw new IllegalArgumentException("Not an EDF file: version field is '" + version + "'");
        }

        String startDate   = readAscii(edf, 168, 8);  // DD.MM.YY
        String startTime   = readAscii(edf, 176, 8);  // HH.MM.SS
        int    headerBytes = parseIntField(edf, 184, 8);
        int    nRecords    = parseIntField(edf, 236, 8);  // may be -1
        double recDuration = parseDoubleField(edf, 244, 8);
        int    ns          = parseIntField(edf, 252, 4);

        if (recDuration <= 0) {
            throw new IllegalArgumentException("Not an EDF file: invalid record duration " + recDuration);
        }
        if (edf.length < headerBytes) {
            throw new IllegalArgumentException(
                    "Not an EDF file: declared header " + headerBytes + " bytes but file is only " + edf.length);
        }

        // --- signal headers (ns × 256 bytes, each field laid out as ns consecutive values) ---
        String[] labels        = readAsciiBlock(edf, 256,            ns, 16);
        // transducer types at 256+ns*16 — not needed
        String[] units         = readAsciiBlock(edf, 256 + ns * 96,  ns, 8);
        double[] physMins      = parseDoubleBlock(edf, 256 + ns * 104, ns, 8);
        double[] physMaxs      = parseDoubleBlock(edf, 256 + ns * 112, ns, 8);
        int[]    digMins       = parseIntBlock(edf, 256 + ns * 120,    ns, 8);
        int[]    digMaxs       = parseIntBlock(edf, 256 + ns * 128,    ns, 8);
        // prefiltering at 256+ns*136 — not needed
        int[]    samplesPerRec = parseIntBlock(edf, 256 + ns * 216,    ns, 8);
        // reserved at 256+ns*224 — not needed

        // --- datetime ---
        LocalDateTime startDatetime = parseEdfDatetime(startDate, startTime);

        // --- how many records to decode ---
        int skipRecords = (int) Math.max(0, Math.floor((double) startSeconds / recDuration));
        int maxRecords = (int) Math.ceil((double) maxSeconds / recDuration);
        int availableRecords = nRecords < 0 ? Integer.MAX_VALUE : Math.max(0, nRecords - skipRecords);
        int recordsToRead = Math.min(availableRecords, maxRecords);

        // Adjust startDatetime for segment offset
        if (skipRecords > 0) {
            double skipSeconds = skipRecords * recDuration;
            startDatetime = startDatetime.plusNanos((long) (skipSeconds * 1_000_000_000L));
        }

        int recordSizeBytes = 0;
        for (int s : samplesPerRec) recordSizeBytes += s * 2;

        // --- decode data records ---
        List<List<Double>> channelSamples = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            channelSamples.add(new ArrayList<>(samplesPerRec[i] * recordsToRead));
        }

        int startOffset = headerBytes + skipRecords * recordSizeBytes;
        for (int rec = 0; rec < recordsToRead; rec++) {
            int recStart = startOffset + rec * recordSizeBytes;
            if (recStart + recordSizeBytes > edf.length) break;  // truncated or nRecords was -1
            int pos = recStart;
            for (int i = 0; i < ns; i++) {
                int nSamples = samplesPerRec[i];
                boolean skip = "EDF Annotations".equals(labels[i]) || "Crc16".equals(labels[i]);
                for (int s = 0; s < nSamples; s++) {
                    short raw = (short) ((edf[pos] & 0xFF) | ((edf[pos + 1] & 0xFF) << 8));
                    pos += 2;
                    if (!skip) {
                        double physical = scale(raw, digMins[i], digMaxs[i], physMins[i], physMaxs[i]);
                        channelSamples.get(i).add(round4(physical));
                    }
                }
            }
        }

        // --- build channel list, trim to maxSeconds exactly ---
        List<WaveformChannel> channels = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            if ("EDF Annotations".equals(labels[i])) continue;
            if ("Crc16".equals(labels[i])) continue;  // integrity checksum, not clinically useful
            if (samplesPerRec[i] == 0) continue;
            double sampleRate = samplesPerRec[i] / recDuration;
            int maxSamples = (int) Math.floor(maxSeconds * sampleRate);
            List<Double> samples = channelSamples.get(i);
            if (samples.size() > maxSamples) {
                samples = new ArrayList<>(samples.subList(0, maxSamples));
            }
            channels.add(new WaveformChannel(labels[i], sampleRate, units[i], samples));
        }

        // duration_seconds = full recording length, not the capped slice
        double durationSeconds = nRecords >= 0 ? nRecords * recDuration : (skipRecords + recordsToRead) * recDuration;

        return new WaveformResult(null, startDatetime.toString(), durationSeconds, channels);
    }

    // --- helpers ---

    static String readAscii(byte[] buf, int offset, int len) {
        return new String(buf, offset, len, StandardCharsets.US_ASCII).trim();
    }

    static String[] readAsciiBlock(byte[] buf, int offset, int ns, int fieldLen) {
        String[] result = new String[ns];
        for (int i = 0; i < ns; i++) {
            result[i] = readAscii(buf, offset + i * fieldLen, fieldLen);
        }
        return result;
    }

    static double[] parseDoubleBlock(byte[] buf, int offset, int ns, int fieldLen) {
        double[] result = new double[ns];
        for (int i = 0; i < ns; i++) {
            result[i] = parseDoubleField(buf, offset + i * fieldLen, fieldLen);
        }
        return result;
    }

    static int[] parseIntBlock(byte[] buf, int offset, int ns, int fieldLen) {
        int[] result = new int[ns];
        for (int i = 0; i < ns; i++) {
            result[i] = parseIntField(buf, offset + i * fieldLen, fieldLen);
        }
        return result;
    }

    static int parseIntField(byte[] buf, int offset, int len) {
        String s = readAscii(buf, offset, len);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "EDF parse error: expected int at offset " + offset + ", got '" + s + "'");
        }
    }

    static double parseDoubleField(byte[] buf, int offset, int len) {
        String s = readAscii(buf, offset, len);
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "EDF parse error: expected double at offset " + offset + ", got '" + s + "'");
        }
    }

    // EDF century rule: YY 00-84 → 2000+YY, YY 85-99 → 1900+YY
    static LocalDateTime parseEdfDatetime(String dateStr, String timeStr) {
        String[] dp = dateStr.split("\\.");
        String[] tp = timeStr.split("\\.");
        if (dp.length != 3 || tp.length != 3) {
            throw new IllegalArgumentException(
                    "EDF date/time format invalid: '" + dateStr + "' / '" + timeStr + "'");
        }
        int day   = Integer.parseInt(dp[0]);
        int month = Integer.parseInt(dp[1]);
        int yy    = Integer.parseInt(dp[2]);
        int year  = yy <= 84 ? 2000 + yy : 1900 + yy;
        int hour  = Integer.parseInt(tp[0]);
        int min   = Integer.parseInt(tp[1]);
        int sec   = Integer.parseInt(tp[2]);
        return LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, min, sec));
    }

    static double scale(short digital, int digMin, int digMax, double physMin, double physMax) {
        if (digMax == digMin) return physMin;
        return physMin + ((double) (digital - digMin)) * (physMax - physMin) / (digMax - digMin);
    }

    static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
