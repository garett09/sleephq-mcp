package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import com.adriangarett.sleephqmcp.support.EdfParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OscarWaveformStatistics {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private OscarWaveformStatistics() {}

    public static Map<String, ChannelStatistics> fromPld(Path pldPath, int percentile) throws Exception {
        byte[] bytes = Files.readAllBytes(pldPath);
        WaveformResult result = EdfParser.parse(bytes, 0, Integer.MAX_VALUE);
        LocalDateTime base = LocalDateTime.parse(result.startDatetime().substring(0, 19));
        Map<String, ChannelStatistics> stats = new LinkedHashMap<>();
        for (WaveformChannel channel : result.channels()) {
            String key = mapLabelToField(channel.label());
            if (key == null || channel.samples() == null || channel.samples().isEmpty()) {
                continue;
            }
            stats.put(key, compute(key, channel.unit(), channel.samples(), channel.sampleRate(), base, percentile));
        }
        return stats;
    }

    public static Map<String, ChannelStatistics> fromBrp(Path brpPath, int percentile) throws Exception {
        byte[] bytes = Files.readAllBytes(brpPath);
        WaveformResult result = EdfParser.parse(bytes, 0, Integer.MAX_VALUE);
        LocalDateTime base = LocalDateTime.parse(result.startDatetime().substring(0, 19));
        Map<String, ChannelStatistics> stats = new LinkedHashMap<>();
        for (WaveformChannel channel : result.channels()) {
            String key = mapBrpLabel(channel.label());
            if (key == null || channel.samples() == null || channel.samples().isEmpty()) {
                continue;
            }
            stats.put(key, compute(key, channel.unit(), channel.samples(), channel.sampleRate(), base, percentile));
        }
        return stats;
    }

    static String mapLabelToField(String label) {
        if (label == null) {
            return null;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.startsWith("tidvol")) {
            return "tidal_volume";
        }
        if (lower.startsWith("resprate")) {
            return "resp_rate";
        }
        if (lower.startsWith("minvent")) {
            return "minute_vent";
        }
        if (lower.startsWith("flowhires") || lower.startsWith("flowrate2") || lower.equals("flow rate (hi-res)")) {
            return "flow_rate_hi_res";
        }
        if (lower.startsWith("flow")) {
            return "flow";
        }
        if (lower.startsWith("press")) {
            return "pressure";
        }
        if (lower.startsWith("leak")) {
            return "leak";
        }
        return null;
    }

    private static String mapBrpLabel(String label) {
        if (label == null) {
            return null;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.startsWith("flow")) {
            return "flow_brp";
        }
        if (lower.startsWith("press")) {
            return "pressure_brp";
        }
        return null;
    }

    private static ChannelStatistics compute(
            String fieldName,
            String unit,
            List<Double> samples,
            double sampleRate,
            LocalDateTime base,
            int percentile) {
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int minIdx = 0;
        int maxIdx = 0;
        List<Double> sorted = new ArrayList<>(samples);
        for (int i = 0; i < samples.size(); i++) {
            double v = samples.get(i);
            if (Double.isNaN(v)) {
                continue;
            }
            sum += v;
            if (v < min) {
                min = v;
                minIdx = i;
            }
            if (v > max) {
                max = v;
                maxIdx = i;
            }
        }
        sorted.removeIf(v -> Double.isNaN(v));
        sorted.sort(Double::compareTo);
        double avg = sorted.isEmpty() ? 0 : sum / sorted.size();
        double p = percentileValue(sorted, percentile);
        String minAt = clockAt(base, sampleRate, minIdx);
        String maxAt = clockAt(base, sampleRate, maxIdx);
        int minAtSeconds = offsetSeconds(sampleRate, minIdx);
        int maxAtSeconds = offsetSeconds(sampleRate, maxIdx);
        if (min == Double.POSITIVE_INFINITY) {
            min = 0;
            max = 0;
        }
        ChannelStatistics raw = new ChannelStatistics(fieldName, unit == null ? "" : unit,
                round(avg), round(min), round(max), round(p), minAt, maxAt,
                minAtSeconds, maxAtSeconds, sorted.size());
        return OscarChannelUnitNormalizer.normalize(raw);
    }

    private static double percentileValue(List<Double> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    private static String clockAt(LocalDateTime base, double sampleRate, int index) {
        if (sampleRate <= 0) {
            return "";
        }
        long seconds = (long) (index / sampleRate);
        return base.plusSeconds(seconds).format(CLOCK);
    }

    private static int offsetSeconds(double sampleRate, int index) {
        if (sampleRate <= 0) {
            return ChannelStatistics.OFFSET_UNKNOWN;
        }
        return (int) (index / sampleRate);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
