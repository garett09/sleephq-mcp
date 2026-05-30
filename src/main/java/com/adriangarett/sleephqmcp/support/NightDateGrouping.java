package com.adriangarett.sleephqmcp.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Night/session date logic. CPAP sessions are grouped by ResMed's {@code DATALOG/<YYYYMMDD>/} folder
 * (authoritative therapy-night grouping — no noon-split needed). Flat O2-ring files have no folder, so
 * they are grouped by a noon-split on their filename timestamp: a session belongs to night {@code D}
 * when its start is in {@code [D 12:00, (D+1) 12:00)}.
 */
public final class NightDateGrouping {

    private static final Pattern STAMP = Pattern.compile("(\\d{8})_?(\\d{6})");

    private NightDateGrouping() {}

    public static String cleanDate(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Invalid date format: " + date + ". Expected YYYY-MM-DD.");
        }
        return date.replace("-", "");
    }

    /** First {@code YYYYMMDD[_]HHMMSS} stamp in {@code text}, or null. */
    public static LocalDateTime parseStamp(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = STAMP.matcher(text);
        if (!m.find()) {
            return null;
        }
        String d = m.group(1);
        String t = m.group(2);
        try {
            return LocalDateTime.of(
                    Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)),
                    Integer.parseInt(d.substring(6, 8)), Integer.parseInt(t.substring(0, 2)),
                    Integer.parseInt(t.substring(2, 4)), Integer.parseInt(t.substring(4, 6)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean inNoonWindow(LocalDateTime start, String date) {
        if (start == null) {
            return false;
        }
        LocalDateTime windowStart = LocalDate.parse(date).atTime(LocalTime.NOON);
        return !start.isBefore(windowStart) && start.isBefore(windowStart.plusDays(1));
    }

    /** True if a SleepHQ file {@code path} attribute points at the DATALOG folder for {@code date}. */
    public static boolean datalogPathMatches(String path, String date) {
        if (path == null) {
            return false;
        }
        return path.toLowerCase(Locale.ROOT).contains("datalog/" + cleanDate(date));
    }
}
