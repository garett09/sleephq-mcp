package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses EDF+ Time-stamped Annotation Lists from {@code EVE.edf} and similar files.
 */
public final class EdfAnnotationParser {

    private static final byte TAL_SEP = 20;
    private static final byte TAL_ONSET_DURATION_SEP = 21;

    private EdfAnnotationParser() {}

    public static DeviceEventResult parse(byte[] edf, String filename) {
        EdfHeader header = EdfBinarySupport.readHeaderForAnnotations(edf);
        byte[] talBytes = EdfBinarySupport.extractAnnotationBytes(edf, header);
        List<ParsedTal> tals = parseTals(talBytes);

        List<DeviceEvent> events = new ArrayList<>();
        LocalDateTime base = header.startDatetime();
        double durationSeconds = computeSessionDurationSeconds(header, tals);

        for (ParsedTal tal : tals) {
            if (tal.annotations().isEmpty()) {
                continue;
            }
            for (String label : tal.annotations()) {
                if (label.isBlank()) {
                    continue;
                }
                events.add(toDeviceEvent(base, tal.onsetSeconds(), tal.durationSeconds(), label));
            }
        }

        return new DeviceEventResult(
                filename != null ? filename : "",
                base.toString(),
                durationSeconds,
                "device_eve",
                events
        );
    }

    static List<ParsedTal> parseTals(byte[] bytes) {
        List<ParsedTal> result = new ArrayList<>();
        int pos = 0;
        while (pos < bytes.length) {
            while (pos < bytes.length && bytes[pos] == 0) {
                pos++;
            }
            if (pos >= bytes.length) {
                break;
            }
            if (bytes[pos] != '+' && bytes[pos] != '-') {
                pos++;
                continue;
            }
            int talStart = pos;
            int onsetEnd = pos + 1;
            while (onsetEnd < bytes.length && bytes[onsetEnd] != TAL_ONSET_DURATION_SEP
                    && bytes[onsetEnd] != TAL_SEP && bytes[onsetEnd] != 0) {
                onsetEnd++;
            }
            String onsetStr = new String(bytes, pos, onsetEnd - pos, StandardCharsets.US_ASCII);
            double onset = parseSeconds(onsetStr);
            pos = onsetEnd;

            double duration = 0.0;
            if (pos < bytes.length && bytes[pos] == TAL_ONSET_DURATION_SEP) {
                pos++;
                int durEnd = pos;
                while (durEnd < bytes.length && bytes[durEnd] != TAL_SEP && bytes[durEnd] != 0) {
                    durEnd++;
                }
                if (durEnd > pos) {
                    String durStr = new String(bytes, pos, durEnd - pos, StandardCharsets.US_ASCII);
                    duration = parseSeconds(durStr);
                }
                pos = durEnd;
            }

            List<String> annotations = new ArrayList<>();
            while (pos < bytes.length && bytes[pos] == TAL_SEP) {
                pos++;
                if (pos < bytes.length && bytes[pos] == 0) {
                    pos++;
                    break;
                }
                int annStart = pos;
                while (pos < bytes.length && bytes[pos] != TAL_SEP && bytes[pos] != 0) {
                    pos++;
                }
                if (pos > annStart) {
                    String ann = new String(bytes, annStart, pos - annStart, StandardCharsets.US_ASCII).trim();
                    if (!ann.isEmpty()) {
                        annotations.add(ann);
                    }
                }
            }
            if (pos < bytes.length && bytes[pos] == 0) {
                pos++;
            }
            if (!annotations.isEmpty() || onset != 0.0 || duration > 0) {
                result.add(new ParsedTal(onset, duration, annotations));
            }
            if (pos == talStart) {
                pos++;
            }
        }
        return result;
    }

    static String normalizeCode(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.contains("obstructive") && lower.contains("apnea")) {
            return "OA";
        }
        if (lower.contains("central") || lower.contains("clear airway")) {
            return "CA";
        }
        if (lower.contains("hypopnea")) {
            return "H";
        }
        if (lower.contains("apnea")) {
            return "A";
        }
        if (lower.contains("flow limitation") || lower.contains("flow limit")) {
            return "FL";
        }
        if (lower.contains("large leak")) {
            return "LL";
        }
        if (lower.contains("rera")) {
            return "RE";
        }
        if (lower.contains("cheyne")) {
            return "CSR";
        }
        if (lower.contains("pressure pulse")) {
            return "PP";
        }
        return null;
    }

    private static DeviceEvent toDeviceEvent(LocalDateTime base, double startSec, double durationSec, String label) {
        LocalDateTime eventTime = base.plusNanos((long) (startSec * 1_000_000_000L));
        long hr = (long) (startSec / 3600);
        long min = (long) ((startSec % 3600) / 60);
        long sec = (long) (startSec % 60);
        String offset = String.format("%02d:%02d:%02d", hr, min, sec);
        return new DeviceEvent(offset, startSec, durationSec, eventTime.toString(), label, normalizeCode(label));
    }

    private static double parseSeconds(String s) {
        if (s == null || s.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(s.trim());
    }

    private static double computeSessionDurationSeconds(EdfHeader header, List<ParsedTal> tals) {
        if (header.recordDurationSeconds() > 0 && header.nRecords() > 0) {
            return header.nRecords() * header.recordDurationSeconds();
        }
        double maxEnd = 0.0;
        for (ParsedTal tal : tals) {
            for (String label : tal.annotations()) {
                if (!label.isBlank()) {
                    maxEnd = Math.max(maxEnd, tal.onsetSeconds() + tal.durationSeconds());
                }
            }
        }
        return maxEnd;
    }

    record ParsedTal(double onsetSeconds, double durationSeconds, List<String> annotations) {}
}
