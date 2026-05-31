package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        double maxVal = Math.max(stat.max(), stat.percentile());
        if (maxVal >= TIDAL_MILLILITER_MIN || unitImpliesMilliliters(stat.unit())) {
            return withUnit(stat, "mL", 1.0);
        }
        if (maxVal >= TIDAL_LITER_SCALE_MAX) {
            return withUnit(stat, "L", 1.0);
        }
        return scale(stat, 1000.0, "mL");
    }

    private static ChannelStatistics normalizeLitersPerMinute(ChannelStatistics stat) {
        String unit = stat.unit() == null ? "" : stat.unit().trim();
        String lower = unit.toLowerCase(Locale.ROOT);
        if (lower.contains("/s") || lower.equals("l/s") || lower.equals("ls")) {
            return scale(stat, 60.0, "L/min");
        }
        if (unit.isBlank() || lower.contains("l")) {
            return withUnit(stat, "L/min", 1.0);
        }
        return stat;
    }

    private static ChannelStatistics normalizeWithCatalogDefault(ChannelStatistics stat) {
        if (stat.unit() != null && !stat.unit().isBlank()) {
            return stat;
        }
        return OscarChannelCatalog.findByFieldName(stat.fieldName())
                .filter(meta -> meta.unit() != null && !meta.unit().isBlank())
                .map(meta -> withUnit(stat, meta.unit(), 1.0))
                .orElse(stat);
    }

    private static boolean unitImpliesMilliliters(String unit) {
        if (unit == null || unit.isBlank()) {
            return false;
        }
        String lower = unit.toLowerCase(Locale.ROOT);
        return lower.contains("ml") || lower.contains("milli");
    }

    private static ChannelStatistics withUnit(ChannelStatistics stat, String unit, double scale) {
        return scale(stat, scale, unit);
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
        String lower = rawUnit == null ? "" : rawUnit.trim().toLowerCase(Locale.ROOT);
        String labelLower = edfLabel == null ? "" : edfLabel.toLowerCase(Locale.ROOT);
        return switch (fieldName) {
            case "leak_rate" -> {
                if (lower.contains("/s") || lower.equals("l/s") || lower.equals("ls")) {
                    yield new UnitConversion("L/min", 60.0);
                }
                if (labelLower.contains("leak") && (rawUnit == null || rawUnit.isBlank())) {
                    yield new UnitConversion("L/min", 60.0);
                }
                if ((rawUnit == null || rawUnit.isBlank()) && rawSamples != null && leakSamplesLookLikeLitersPerSecond(rawSamples)) {
                    yield new UnitConversion("L/min", 60.0);
                }
                yield new UnitConversion(rawUnit == null || rawUnit.isBlank() ? "L/min" : rawUnit, 1.0);
            }
            case "tidal_volume" -> conversionForTidalVolume(lower, rawUnit, rawSamples);
            default -> new UnitConversion(rawUnit == null ? "" : rawUnit, 1.0);
        };
    }

    private static boolean leakSamplesLookLikeLitersPerSecond(List<Double> rawSamples) {
        double max = sampleMax(rawSamples);
        return max > 0.0 && max < LEAK_LPS_MAGNITUDE_MAX;
    }

    private static UnitConversion conversionForTidalVolume(String lower, String rawUnit, List<Double> rawSamples) {
        if (lower.contains("ml") || lower.contains("milli")) {
            return new UnitConversion("mL", 1.0);
        }
        double max = sampleMax(rawSamples);
        if (max >= TIDAL_MILLILITER_MIN) {
            return new UnitConversion("mL", 1.0);
        }
        if (rawUnit == null || rawUnit.isBlank() || lower.contains("l")) {
            return new UnitConversion("mL", 1000.0);
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
