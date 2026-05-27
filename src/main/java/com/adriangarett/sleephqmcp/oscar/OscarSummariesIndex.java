package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Reads {@code Summaries.xml.gz} for session calendar coverage and channel lists.
 */
public final class OscarSummariesIndex {

    private static final Pattern SESSION_PATTERN = Pattern.compile(
            "<session\\s+[^>]*events=\"(\\d+)\"[^>]*id=\"(\\d+)\"[^>]*enabled=\"(\\d+)\"[^>]*"
                    + "first=\"(\\d+)\"[^>]*last=\"(\\d+)\"[^>]*>\\s*"
                    + "<channels>([^<]*)</channels>\\s*"
                    + "<settings>([^<]*)</settings>",
            Pattern.DOTALL);

    private final List<OscarSessionIndexEntry> sessions;

    public OscarSummariesIndex(List<OscarSessionIndexEntry> sessions) {
        this.sessions = List.copyOf(sessions);
    }

    public static OscarSummariesIndex load(Path deviceFolder) throws IOException {
        Path xmlGz = deviceFolder.resolve("Summaries.xml.gz");
        if (!Files.isRegularFile(xmlGz)) {
            return new OscarSummariesIndex(List.of());
        }
        String xml;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(xmlGz))) {
            xml = new String(in.readAllBytes());
        }
        return parse(xml);
    }

    static OscarSummariesIndex parse(String xml) {
        Matcher matcher = SESSION_PATTERN.matcher(xml);
        List<OscarSessionIndexEntry> entries = new ArrayList<>();
        while (matcher.find()) {
            boolean hasEvents = "1".equals(matcher.group(1));
            long sessionId = Long.parseLong(matcher.group(2));
            boolean enabled = "1".equals(matcher.group(3));
            long firstMs = Long.parseLong(matcher.group(4));
            long lastMs = Long.parseLong(matcher.group(5));
            List<Integer> channels = parseChannelList(matcher.group(6));
            List<Integer> settings = parseChannelList(matcher.group(7));
            entries.add(new OscarSessionIndexEntry(
                    sessionId,
                    enabled,
                    hasEvents,
                    Instant.ofEpochMilli(firstMs),
                    Instant.ofEpochMilli(lastMs),
                    channels,
                    settings));
        }
        return new OscarSummariesIndex(entries);
    }

    public List<OscarSessionIndexEntry> sessions() {
        return sessions;
    }

    public Optional<OscarSessionIndexEntry> findPrimarySessionForDate(LocalDate date, ZoneId zone) {
        OscarSessionIndexEntry best = null;
        long bestDuration = -1;
        for (OscarSessionIndexEntry entry : sessions) {
            if (!entry.enabled()) {
                continue;
            }
            LocalDate startDate = entry.firstInstant().atZone(zone).toLocalDate();
            LocalDate endDate = entry.lastInstant().atZone(zone).toLocalDate();
            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }
            long duration = entry.lastInstant().toEpochMilli() - entry.firstInstant().toEpochMilli();
            if (duration > bestDuration) {
                bestDuration = duration;
                best = entry;
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<Integer> parseChannelList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String[] parts = csv.split(",");
        List<Integer> ids = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ids.add(Integer.parseInt(trimmed, 16));
        }
        return List.copyOf(ids);
    }
}
