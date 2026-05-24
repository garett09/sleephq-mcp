package com.adriangarett.sleephqmcp.support;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class TimeParams {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TimeParams() {
    }

    /**
     * Parse an HH:mm:ss time string, returning {@code null} for null or blank input.
     * Throws {@link IllegalArgumentException} for malformed values.
     */
    public static LocalTime parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid time '" + value + "' — expected HH:mm:ss (24-hour)", e);
        }
    }

    /**
     * Parses HH:mm:ss; throws if null or blank.
     */
    public static LocalTime requireTime(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " is required (HH:mm:ss)");
        }
        try {
            return LocalTime.parse(value, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid " + paramName + " '" + value + "' — expected HH:mm:ss (24-hour)", e);
        }
    }

    public static String format(LocalTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
