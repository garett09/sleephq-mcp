package com.adriangarett.sleephqmcp.support;

import java.util.regex.Pattern;

/**
 * Validates identifiers and dates before they are used in SleepHQ URL paths or query strings.
 * Rejects path traversal, slashes, and other characters outside a conservative allowlist.
 */
public final class SleepHqPathParams {

    private static final int MAX_ID_LENGTH = 128;
    private static final Pattern RESOURCE_ID = Pattern.compile("^[a-zA-Z0-9_-]{1," + MAX_ID_LENGTH + "}$");
    private static final Pattern CALENDAR_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern OPTIONAL_QUERY_TOKEN = Pattern.compile("^[a-zA-Z0-9_-]{0,64}$");

    private SleepHqPathParams() {
    }

    public static String requireResourceId(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " is required");
        }
        if (!RESOURCE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(paramName + " has invalid format");
        }
        return value;
    }

    public static String requireCalendarDate(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " is required");
        }
        if (!CALENDAR_DATE.matcher(value).matches()) {
            throw new IllegalArgumentException(paramName + " must be YYYY-MM-DD");
        }
        return value;
    }

    /**
     * Validates when non-null and non-blank; otherwise returns null.
     */
    public static String optionalCalendarDate(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireCalendarDate(value, paramName);
    }

    /**
     * Validates when non-null and non-blank; otherwise returns null.
     */
    public static String optionalResourceId(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireResourceId(value, paramName);
    }

    /**
     * Validates optional query tokens (e.g. sort_order, bucket).
     */
    public static String optionalQueryToken(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!OPTIONAL_QUERY_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(paramName + " has invalid format");
        }
        return value;
    }

    public static String requirePathSegment(String value, String paramName, java.util.regex.Pattern allowed) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " is required");
        }
        if (!allowed.matcher(value).matches()) {
            throw new IllegalArgumentException(paramName + " has invalid format");
        }
        return value;
    }
}
