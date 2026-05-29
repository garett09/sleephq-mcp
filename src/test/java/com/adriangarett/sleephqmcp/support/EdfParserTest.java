package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EdfParserTest {

    @Test
    void parse_withOffset_skipsRecordsAndShiftsTimestamp() {
        // Construct a mock EDF file with:
        // ns = 1
        // nRecords = 10
        // recDuration = 1.0 second
        // samplesPerRec = 2
        // total samples = 20 (2 per record)
        byte[] edf = new byte[256 + 256 + 10 * 2 * 2]; // 512 header + 40 data bytes
        Arrays.fill(edf, (byte) ' ');

        // Global Header (256 bytes)
        writeString(edf, 0, "0"); // Version
        writeString(edf, 168, "20.05.26"); // Date
        writeString(edf, 176, "21.09.00"); // Time
        writeString(edf, 184, "512"); // Header bytes
        writeString(edf, 236, "10"); // Number of records
        writeString(edf, 244, "1.0"); // Record duration
        writeString(edf, 252, "1"); // Number of signals (ns)

        // Signal Header (256 bytes)
        writeString(edf, 256, "Flow"); // Label
        writeString(edf, 256 + 96, "L/s"); // Unit
        writeString(edf, 256 + 104, "-5.0"); // Phys min
        writeString(edf, 256 + 112, "5.0"); // Phys max
        writeString(edf, 256 + 120, "-2048"); // Dig min
        writeString(edf, 256 + 128, "2047"); // Dig max
        writeString(edf, 256 + 216, "2"); // Samples per record (2 Hz)

        // Write sequential data samples: short raw values (2 bytes each, little-endian)
        // Record 0: raw 100, 200
        // Record 1: raw 300, 400
        // Record 2: raw 500, 600
        // ...
        // Record 9: raw 1900, 2000
        int pos = 512;
        for (int rec = 0; rec < 10; rec++) {
            short val1 = (short) (rec * 200 + 100);
            short val2 = (short) (rec * 200 + 200);
            edf[pos++] = (byte) (val1 & 0xFF);
            edf[pos++] = (byte) ((val1 >> 8) & 0xFF);
            edf[pos++] = (byte) (val2 & 0xFF);
            edf[pos++] = (byte) ((val2 >> 8) & 0xFF);
        }

        // Parse with startSeconds = 2, maxSeconds = 3
        // This should skip 2 records (Record 0, Record 1)
        // Read 3 records: Record 2 (500, 600), Record 3 (700, 800), Record 4 (900, 1000)
        // Total samples expected = 6
        WaveformResult result = EdfParser.parse(edf, 2, 3);

        assertThat(result.startDatetime()).isEqualTo("2026-05-20T21:09:02"); // shifted 2 seconds
        // 3 records read (skip 2, maxSeconds 3) at 1.0s each = 3.0s of returned data
        assertThat(result.durationSeconds()).isEqualTo(3.0);

        assertThat(result.channels()).hasSize(1);
        WaveformChannel flow = result.channels().getFirst();
        assertThat(flow.label()).isEqualTo("Flow");
        assertThat(flow.sampleRate()).isEqualTo(2.0);

        // Dig min = -2048, Dig max = 2047, Phys min = -5.0, Phys max = 5.0
        // raw = 500 -> scaled physical
        double expectedPhys1 = -5.0 + (500.0 - (-2048.0)) * 10.0 / 4095.0;
        double expectedPhys2 = -5.0 + (600.0 - (-2048.0)) * 10.0 / 4095.0;

        assertThat(flow.samples()).hasSize(6);
        assertThat(flow.samples().get(0)).isCloseTo(expectedPhys1, org.assertj.core.api.Assertions.within(0.001));
        assertThat(flow.samples().get(1)).isCloseTo(expectedPhys2, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void parse_fullRead_reportsFullDuration() {
        byte[] edf = new byte[256 + 256 + 10 * 2 * 2];
        Arrays.fill(edf, (byte) ' ');
        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, "10");
        writeString(edf, 244, "1.0");
        writeString(edf, 252, "1");
        writeString(edf, 256, "Flow");
        writeString(edf, 256 + 96, "L/s");
        writeString(edf, 256 + 104, "-5.0");
        writeString(edf, 256 + 112, "5.0");
        writeString(edf, 256 + 120, "-2048");
        writeString(edf, 256 + 128, "2047");
        writeString(edf, 256 + 216, "2");
        int pos = 512;
        for (int rec = 0; rec < 10; rec++) {
            short val1 = (short) (rec * 200 + 100);
            short val2 = (short) (rec * 200 + 200);
            edf[pos++] = (byte) (val1 & 0xFF);
            edf[pos++] = (byte) ((val1 >> 8) & 0xFF);
            edf[pos++] = (byte) (val2 & 0xFF);
            edf[pos++] = (byte) ((val2 >> 8) & 0xFF);
        }

        WaveformResult result = EdfParser.parse(edf, 0, 100);

        assertThat(result.startDatetime()).isEqualTo("2026-05-20T21:09"); // LocalDateTime.toString() omits :00 seconds
        assertThat(result.durationSeconds()).isEqualTo(10.0);
        assertThat(result.channels().getFirst().samples()).hasSize(20);
    }

    @Test
    void parseFlowChannel_matchesFlowFromFullParse() {
        byte[] edf = new byte[256 + 256 + 10 * 2 * 2];
        Arrays.fill(edf, (byte) ' ');
        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, "10");
        writeString(edf, 244, "1.0");
        writeString(edf, 252, "1");
        writeString(edf, 256, "Flow");
        writeString(edf, 256 + 96, "L/s");
        writeString(edf, 256 + 104, "-5.0");
        writeString(edf, 256 + 112, "5.0");
        writeString(edf, 256 + 120, "-2048");
        writeString(edf, 256 + 128, "2047");
        writeString(edf, 256 + 216, "2");
        int pos = 512;
        for (int rec = 0; rec < 10; rec++) {
            short val1 = (short) (rec * 200 + 100);
            short val2 = (short) (rec * 200 + 200);
            edf[pos++] = (byte) (val1 & 0xFF);
            edf[pos++] = (byte) ((val1 >> 8) & 0xFF);
            edf[pos++] = (byte) (val2 & 0xFF);
            edf[pos++] = (byte) ((val2 >> 8) & 0xFF);
        }

        WaveformResult full = EdfParser.parse(edf, 2, 3);
        WaveformResult flowOnly = EdfParser.parseFlowChannel(edf, 2, 3);

        assertThat(flowOnly.channels()).hasSize(1);
        assertThat(flowOnly.channels().getFirst().samples())
                .isEqualTo(full.channels().getFirst().samples());
    }

    @Test
    void parse_truncatedFile_reportsDurationOfCompletedRecordsOnly() {
        // 10-record header but only 5 records of actual data bytes
        int headerBytes = 256 + 256;
        int fullDataBytes = 10 * 2 * 2; // 10 records × 2 samples × 2 bytes
        int truncatedDataBytes = 5 * 2 * 2; // only 5 records present
        byte[] edf = new byte[headerBytes + truncatedDataBytes];
        Arrays.fill(edf, (byte) ' ');

        writeString(edf, 0, "0");
        writeString(edf, 168, "20.05.26");
        writeString(edf, 176, "21.09.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, "10"); // declares 10 records, but only 5 are present
        writeString(edf, 244, "1.0");
        writeString(edf, 252, "1");
        writeString(edf, 256, "Flow");
        writeString(edf, 256 + 96, "L/s");
        writeString(edf, 256 + 104, "-5.0");
        writeString(edf, 256 + 112, "5.0");
        writeString(edf, 256 + 120, "-2048");
        writeString(edf, 256 + 128, "2047");
        writeString(edf, 256 + 216, "2");
        int pos = headerBytes;
        for (int rec = 0; rec < 5; rec++) {
            short val1 = (short) (rec * 200 + 100);
            short val2 = (short) (rec * 200 + 200);
            edf[pos++] = (byte) (val1 & 0xFF);
            edf[pos++] = (byte) ((val1 >> 8) & 0xFF);
            edf[pos++] = (byte) (val2 & 0xFF);
            edf[pos++] = (byte) ((val2 >> 8) & 0xFF);
        }

        WaveformResult result = EdfParser.parse(edf, 0, 100);

        // Only 5 records actually present → 5.0s returned, not the declared 10.0s
        assertThat(result.durationSeconds()).isEqualTo(5.0);
        assertThat(result.channels().getFirst().samples()).hasSize(10); // 5 records × 2 samples
    }

    private void writeString(byte[] buf, int offset, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
    }
}
