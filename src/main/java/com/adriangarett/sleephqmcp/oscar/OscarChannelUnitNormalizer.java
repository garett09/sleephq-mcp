package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;

import java.util.List;
import java.util.Locale;

/**
 * Aligns reported units with value magnitude and catalog conventions (e.g. tidal mL, leak L/min).
 */
public final class OscarChannelUnitNormalizer {

    /** Below this max, tidal samples are treated as liters and reported in mL unless clearly large-liter. */
    private static final double TIDAL_LITER_SCALE_MAX = 3.0;

    /** At or above this max, tidal samples are already in milliliters. */
    private static final double TIDAL_MILLILITER_MIN = 25.0;

    private OscarChannelUnitNormalizer() {}

    public static ChannelStatistics normalize(ChannelStatistics stat) {
        if (stat == null) {
            return stat;
        }
        return switch (stat.fieldName()) {
            case "tidal_volume" -> normalizeTidalVolume(stat);
            case "leak", "flow_brp", "flow" -> normalizeLitersPerMinute(stat);
            default -> normalizeWithCatalogDefault(stat);
        };
    }

    private static ChannelStatistics normalizeTidalVolume(ChannelStatistics stat) {
        // max() >= percentile() by definition, so stat.max() is the magnitude both paths key on.
        UnitConversion c = tidalConversion(stat.unit(), stat.max());
        return scale(stat, c.factor(), c.unit());
    }

    private static ChannelStatistics normalizeLitersPerMinute(ChannelStatistics stat) {
        UnitConversion c = leakConversion(stat.fieldName(), stat.unit(), stat.max(), null);
        return scale(stat, c.factor(), c.unit());
    }

    private static ChannelStatistics normalizeWithCatalogDefault(ChannelStatistics stat) {
        return stat;
    }

    private static ChannelStatistics scale(ChannelStatistics stat, double factor, String unit) {
        return new ChannelStatistics(
                stat.fieldName(),
                unit,
                round(stat.avg() * factor),
                round(stat.min() * factor),
                round(stat.max() * factor),
                round(stat.percentile() * factor),
                scaleValue(stat.p995(), factor),
                scaleValue(stat.median(), factor),
                stat.minAt(),
                stat.maxAt(),
                stat.minAtSeconds(),
                stat.maxAtSeconds(),
                stat.sampleCount());
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double scaleValue(double value, double factor) {
        return Double.isNaN(value) ? Double.NaN : round(value * factor);
    }

    /** Display-unit conversion for a {@code get-sleephq-night} field given its raw EDF unit. */
    public record UnitConversion(String unit, double factor) {}

    /** ResMed PLD leak samples in L/s are typically &lt; 5 before ×60; values already in L/min are higher. */
    private static final double LEAK_LPS_MAGNITUDE_MAX = 5.0;

    /**
     * Display unit + multiplicative factor for raw PLD samples. ResMed PLD stores leak in L/s and
     * tidal volume in L; SleepHQ/AirView show L/min and mL. Other fields are identity.
     */
    public static UnitConversion conversionFor(String fieldName, String rawUnit) {
        return conversionFor(fieldName, rawUnit, null, null);
    }

    public static UnitConversion conversionFor(String fieldName, String rawUnit, List<Double> rawSamples) {
        return conversionFor(fieldName, rawUnit, rawSamples, null);
    }

    /**
     * Like {@link #conversionFor(String, String)} but uses raw sample magnitude and PLD label when the
     * EDF unit field is blank (common on {@code Leak.2s} / {@code TidVol.2s}).
     */
    public static UnitConversion conversionFor(String fieldName, String rawUnit, List<Double> rawSamples,
                                               String edfLabel) {
        return switch (fieldName) {
            case "leak_rate" -> leakConversion(fieldName, rawUnit, sampleMax(rawSamples), edfLabel);
            case "tidal_volume" -> tidalConversion(rawUnit, sampleMax(rawSamples));
            default -> new UnitConversion(rawUnit == null ? "" : rawUnit, 1.0);
        };
    }

    /**
     * Single source of truth for L/s → L/min inference, shared by the OSCAR ({@code normalize}) and
     * sleephq-night ({@code conversionFor}) paths so leak units cannot drift between the two tools.
     * The magnitude/label fallback for blank units applies only to leak fields; flow channels keep
     * identity unless their unit string explicitly says {@code /s}.
     */
    private static UnitConversion leakConversion(String fieldName, String rawUnit, double maxMagnitude,
                                                 String label) {
        String lower = rawUnit == null ? "" : rawUnit.trim().toLowerCase(Locale.ROOT);
        String labelLower = label == null ? "" : label.toLowerCase(Locale.ROOT);
        boolean blank = rawUnit == null || rawUnit.isBlank();
        if (lower.contains("/s") || lower.equals("l/s") || lower.equals("ls")) {
            return new UnitConversion("L/min", 60.0);
        }
        boolean isLeak = "leak".equals(fieldName) || "leak_rate".equals(fieldName);
        if (isLeak && blank && labelLower.contains("leak")) {
            return new UnitConversion("L/min", 60.0);
        }
        if (isLeak && blank && maxMagnitude > 0.0 && maxMagnitude < LEAK_LPS_MAGNITUDE_MAX) {
            return new UnitConversion("L/min", 60.0);
        }
        return new UnitConversion(blank ? "L/min" : rawUnit, 1.0);
    }

    /**
     * Single source of truth for tidal-volume L ↔ mL inference, shared by both paths. {@code <3 L}
     * scales to mL; {@code 3..25} keeps L; {@code >=25} (or an explicit mL unit) is already mL.
     */
    private static UnitConversion tidalConversion(String rawUnit, double maxMagnitude) {
        String lower = rawUnit == null ? "" : rawUnit.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("ml") || lower.contains("milli")) {
            return new UnitConversion("mL", 1.0);
        }
        if (maxMagnitude >= TIDAL_MILLILITER_MIN) {
            return new UnitConversion("mL", 1.0);
        }
        if (maxMagnitude >= TIDAL_LITER_SCALE_MAX) {
            return new UnitConversion("L", 1.0);
        }
        return new UnitConversion("mL", 1000.0);
    }

    private static double sampleMax(List<Double> rawSamples) {
        if (rawSamples == null) {
            return 0.0;
        }
        double max = 0.0;
        for (Double v : rawSamples) {
            if (v != null && !Double.isNaN(v)) {
                max = Math.max(max, v);
            }
        }
        return max;
    }
}
