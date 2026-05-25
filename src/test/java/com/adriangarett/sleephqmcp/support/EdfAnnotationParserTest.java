package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EdfAnnotationParserTest {

    @Test
    void parseTals_extractsOnsetDurationAndLabel() {
        byte[] tal = "+120.5\u00152.0\u0014Obstructive Apnea\u0014\u0000".getBytes(StandardCharsets.US_ASCII);
        var tals = EdfAnnotationParser.parseTals(tal);
        assertThat(tals).hasSize(1);
        assertThat(tals.get(0).onsetSeconds()).isEqualTo(120.5);
        assertThat(tals.get(0).durationSeconds()).isEqualTo(2.0);
        assertThat(tals.get(0).annotations()).containsExactly("Obstructive Apnea");
    }

    @Test
    void parseTals_skipsTimeKeepingOnlyTal() {
        byte[] tal = "+0\u0014\u0014\u0000+30\u001510\u0014Hypopnea\u0014\u0000".getBytes(StandardCharsets.US_ASCII);
        var tals = EdfAnnotationParser.parseTals(tal);
        assertThat(tals).hasSize(1);
        assertThat(tals.get(0).onsetSeconds()).isEqualTo(30.0);
        assertThat(tals.get(0).annotations()).containsExactly("Hypopnea");
    }

    @Test
    void normalizeCode_mapsObstructiveApneaToOa() {
        assertThat(EdfAnnotationParser.normalizeCode("Obstructive Apnea")).isEqualTo("OA");
        assertThat(EdfAnnotationParser.normalizeCode("Central Apnea")).isEqualTo("CA");
        assertThat(EdfAnnotationParser.normalizeCode("Hypopnea")).isEqualTo("H");
    }

    @Test
    void parse_minimalEveLikeEdf_returnsDeviceEvents() {
        byte[] edf = buildAnnotationOnlyEdf("+60\u001510\u0014Obstructive Apnea\u0014\u0000", "1.0", "1");
        DeviceEventResult result = EdfAnnotationParser.parse(edf, "test_EVE.edf");
        assertThat(result.source()).isEqualTo("device_eve");
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).code()).isEqualTo("OA");
        assertThat(result.events().get(0).startSeconds()).isEqualTo(60.0);
        assertThat(result.events().get(0).durationSeconds()).isEqualTo(10.0);
    }

    @Test
    void parse_zeroRecordDuration_eveStyle_parsesEvents() {
        byte[] edf = buildAnnotationOnlyEdf("+120\u001525\u0014Hypopnea\u0014\u0000", "0", "1");
        DeviceEventResult result = EdfAnnotationParser.parse(edf, "20260512_EVE.edf");
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).code()).isEqualTo("H");
        assertThat(result.durationSeconds()).isGreaterThanOrEqualTo(145.0);
    }

    @Test
    void parse_zeroRecordDurationAndZeroNRecords_stillParsesTalEvents() {
        byte[] edf = buildAnnotationOnlyEdf("+90\u001510\u0014Obstructive Apnea\u0014\u0000", "0", "0");
        DeviceEventResult result = EdfAnnotationParser.parse(edf, "EVE_zero_hdr.edf");
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).code()).isEqualTo("OA");
        assertThat(result.events().get(0).startSeconds()).isEqualTo(90.0);
    }

    private static byte[] buildAnnotationOnlyEdf(String talPayload, String recordDuration, String nRecords) {
        byte[] talBytes = talPayload.getBytes(StandardCharsets.US_ASCII);
        int annSamples = (talBytes.length + 1) / 2 + 1;
        int headerBytes = 512;
        int recordBytes = annSamples * 2;
        byte[] edf = new byte[headerBytes + recordBytes];
        Arrays.fill(edf, (byte) ' ');

        writeString(edf, 0, "0");
        writeString(edf, 168, "25.05.26");
        writeString(edf, 176, "22.00.00");
        writeString(edf, 184, "512");
        writeString(edf, 236, nRecords);
        writeString(edf, 244, recordDuration);
        writeString(edf, 252, "1");

        writeString(edf, 256, "EDF Annotations");
        writeString(edf, 256 + 96, "");
        writeString(edf, 256 + 104, "-1");
        writeString(edf, 256 + 112, "1");
        writeString(edf, 256 + 120, "-32768");
        writeString(edf, 256 + 128, "32767");
        writeString(edf, 256 + 216, String.valueOf(annSamples));

        System.arraycopy(talBytes, 0, edf, headerBytes, talBytes.length);
        return edf;
    }

    private static void writeString(byte[] buf, int offset, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
    }
}
