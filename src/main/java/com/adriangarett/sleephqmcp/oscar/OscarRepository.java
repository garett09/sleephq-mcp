package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.domain.OscarEdfPaths;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Component
public class OscarRepository {

    private static final Logger log = LoggerFactory.getLogger(OscarRepository.class);

    private final OscarProperties properties;
    private final OscarDeviceResolver deviceResolver;
    private final OscarSqliteDb testDb;

    public OscarRepository(OscarProperties properties) {
        this.properties = properties;
        this.deviceResolver = new OscarDeviceResolver(properties);
        this.testDb = null;
    }

    private OscarRepository(OscarProperties properties, OscarSqliteDb testDb) {
        this.properties = properties;
        this.deviceResolver = new OscarDeviceResolver(properties);
        this.testDb = testDb;
    }

    public static OscarRepository forTesting(OscarProperties properties, OscarSqliteDb db) {
        return new OscarRepository(properties, db);
    }

    @FunctionalInterface
    private interface DbOperation<T> {
        T apply(OscarSqliteDb db);
    }

    private <T> T withDb(DbOperation<T> op, T defaultValue) {
        if (testDb != null) {
            return op.apply(testDb);
        }
        Optional<Path> dbFile = deviceResolver.resolveDbFile();
        if (dbFile.isEmpty()) {
            return defaultValue;
        }
        try (OscarSqliteDb db = OscarSqliteDb.open(dbFile.get()).orElse(null)) {
            if (db == null) {
                return defaultValue;
            }
            return op.apply(db);
        } catch (Exception e) {
            log.warn("OSCAR DB operation failed: {}", e.getMessage());
            return defaultValue;
        }
    }

    public boolean isConfigured() {
        return properties.enabled() && deviceResolver.resolveDbFile().isPresent();
    }

    public boolean isReachable() {
        if (!properties.enabled()) {
            return false;
        }
        return withDb(db -> db.queryOne(
                "SELECT 1 FROM sessions WHERE enabled=1 LIMIT 1",
                rs -> 1).isPresent(), false);
    }

    public Optional<Path> dbFile() {
        return deviceResolver.resolveDbFile();
    }

    public Optional<LocalDate> getLastSessionDate() {
        return withDb(db -> db.queryOne(
                "SELECT MAX(date) FROM daily_summaries",
                rs -> {
                    String d = rs.getString(1);
                    return d == null ? null : LocalDate.parse(d);
                }).flatMap(Optional::ofNullable), Optional.empty());
    }

    public String getLastIndexError() {
        return null;
    }

    public Optional<OscarSessionIndexEntry> findSessionForDate(LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        long startMs = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return withDb(db -> {
            List<long[]> rows = querySessions(db, startMs, endMs);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(buildIndexEntry(db, rows));
        }, Optional.empty());
    }

    public List<OscarSessionIndexEntry> findSessionsInRange(LocalDate start, LocalDate end) {
        ZoneId zone = ZoneId.systemDefault();
        long startMs = start.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return withDb(db -> {
            List<long[]> all = querySessions(db, startMs, endMs);
            // Group by date
            Map<LocalDate, List<long[]>> byDate = new LinkedHashMap<>();
            ZoneId z = ZoneId.systemDefault();
            for (long[] row : all) {
                LocalDate d = Instant.ofEpochMilli(row[2]).atZone(z).toLocalDate();
                byDate.computeIfAbsent(d, k -> new ArrayList<>()).add(row);
            }
            List<OscarSessionIndexEntry> result = new ArrayList<>();
            for (List<long[]> rows : byDate.values()) {
                result.add(buildIndexEntry(db, rows));
            }
            return result;
        }, List.of());
    }

    // Returns rows: [sessions.id, sessions.session_id, sessions.start_time, sessions.end_time]
    private List<long[]> querySessions(OscarSqliteDb db, long startMs, long endMs) {
        return db.query(
                "SELECT id, session_id, start_time, end_time FROM sessions " +
                "WHERE start_time >= ? AND start_time < ? AND enabled=1 ORDER BY start_time",
                rs -> new long[]{rs.getLong("id"), rs.getLong("session_id"),
                        rs.getLong("start_time"), rs.getLong("end_time")},
                startMs, endMs);
    }

    // rows[i] = [sessions.id, sessions.session_id, start_time, end_time]
    private OscarSessionIndexEntry buildIndexEntry(OscarSqliteDb db, List<long[]> rows) {
        long firstOscarId = rows.get(0)[1];
        long minStart = rows.get(0)[2];
        long maxEnd = rows.get(rows.size() - 1)[3];

        // Collect distinct channel codes across all sessions
        List<String> channelCodes = new ArrayList<>();
        for (long[] row : rows) {
            List<String> codes = db.query(
                    "SELECT DISTINCT c.channel_code FROM session_channels sc " +
                    "JOIN channels c ON sc.channel_id = c.channel_id AND c.profile_id = sc.profile_id " +
                    "WHERE sc.session_id = ?",
                    rs -> rs.getString("channel_code"), row[0]);
            for (String code : codes) {
                if (!channelCodes.contains(code)) {
                    channelCodes.add(code);
                }
            }
        }

        boolean hasEvents = rows.stream().anyMatch(row ->
                db.queryOne(
                        "SELECT 1 FROM respiratory_events WHERE session_id=? AND event_type=1 LIMIT 1",
                        rs -> 1, row[0]).isPresent());

        return new OscarSessionIndexEntry(
                firstOscarId,
                true,
                hasEvents,
                Instant.ofEpochMilli(minStart),
                Instant.ofEpochMilli(maxEnd),
                channelCodes,
                List.of());
    }

    public Optional<OscarSession> loadSession(LocalDate date) {
        return withDb(db -> loadSessionFromDb(db, date), Optional.empty());
    }

    public Optional<OscarSession> loadSession(OscarSessionIndexEntry indexEntry) {
        LocalDate date = indexEntry.firstInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return loadSession(date);
    }

    private Optional<OscarSession> loadSessionFromDb(OscarSqliteDb db, LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        long startMs = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        List<long[]> rows = querySessions(db, startMs, endMs);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        // Merge channel summaries across all sessions: weighted avg, global min/max
        Map<String, double[]> sums = new LinkedHashMap<>(); // code -> [weightedSum, totalCount, globalMin, globalMax]
        long totalDurationMs = 0;

        for (long[] row : rows) {
            long sessionRowId = row[0];
            totalDurationMs += (row[3] - row[2]);

            db.query(
                    "SELECT c.channel_code, sc.avg, sc.min, sc.max, sc.count " +
                    "FROM session_channels sc " +
                    "JOIN channels c ON sc.channel_id = c.channel_id AND c.profile_id = sc.profile_id " +
                    "WHERE sc.session_id = ?",
                    rs -> {
                        String code = rs.getString("channel_code");
                        double avg = rs.getDouble("avg");
                        double min = rs.getDouble("min");
                        double max = rs.getDouble("max");
                        double count = rs.getDouble("count");
                        double[] acc = sums.computeIfAbsent(code, k -> new double[]{0, 0, Double.MAX_VALUE, -Double.MAX_VALUE});
                        acc[0] += avg * count;
                        acc[1] += count;
                        acc[2] = Math.min(acc[2], min);
                        acc[3] = Math.max(acc[3], max);
                        return null;
                    }, sessionRowId);
        }

        Map<String, ChannelSummary> channels = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : sums.entrySet()) {
            double[] acc = e.getValue();
            double mergedAvg = acc[1] > 0 ? acc[0] / acc[1] : 0;
            channels.put(e.getKey(), new ChannelSummary(mergedAvg, acc[2], acc[3], null, acc[1], acc[1] > 0 ? acc[0] : 0));
        }

        // Event counts across all sessions
        Map<String, Integer> eventCounts = new LinkedHashMap<>();
        for (long[] row : rows) {
            db.query(
                    "SELECT c.channel_code, COUNT(*) as cnt " +
                    "FROM respiratory_events e " +
                    "JOIN channels c ON e.channel_id = c.channel_id AND c.profile_id = e.profile_id " +
                    "WHERE e.session_id = ? AND e.event_type = 1 " +
                    "GROUP BY e.channel_id, c.channel_code",
                    rs -> {
                        String code = rs.getString("channel_code");
                        String label = OscarChannelMapper.canonicalEventLabel(code);
                        int cnt = rs.getInt("cnt");
                        if (label != null) {
                            eventCounts.merge(label, cnt, Integer::sum);
                        }
                        return null;
                    }, row[0]);
        }

        long firstSessionOscarId = rows.get(0)[1];
        long minStartMs = rows.get(0)[2];
        long durationSeconds = totalDurationMs / 1000;

        return Optional.of(new OscarSession(
                date.toString(),
                firstSessionOscarId,
                minStartMs,
                durationSeconds,
                channels,
                eventCounts));
    }

    public Optional<Map<String, Double>> loadSummaryHeader(OscarSessionIndexEntry session) {
        LocalDate date = session.firstInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        ZoneId zone = ZoneId.systemDefault();
        long startMs = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return withDb(db -> {
            List<long[]> rows = querySessions(db, startMs, endMs);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            long sessionRowId = rows.get(0)[0];
            Map<String, Double> settings = new LinkedHashMap<>();
            db.query(
                    "SELECT c.channel_code, ss.value " +
                    "FROM session_settings ss " +
                    "JOIN channels c ON ss.channel_id = c.channel_id AND c.profile_id = ss.profile_id " +
                    "WHERE ss.session_id = ?",
                    rs -> {
                        settings.put(rs.getString("channel_code"), rs.getDouble("value"));
                        return null;
                    }, sessionRowId);
            return settings.isEmpty() ? Optional.empty() : Optional.of(settings);
        }, Optional.empty());
    }

    public Map<String, OscarChannelHistogram> loadChannelHistograms(LocalDate date) {
        ZoneId zone = ZoneId.systemDefault();
        long startMs = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return withDb(db -> {
            List<long[]> rows = querySessions(db, startMs, endMs);
            Map<String, TreeMap<Integer, Long>> merged = new LinkedHashMap<>();
            Map<String, Double> gains = new LinkedHashMap<>();

            for (long[] row : rows) {
                // Materialize outer rows first to avoid nested ResultSet issue
                record ScRow(long scId, String code, double gain) {}
                List<ScRow> scRows = db.query(
                        "SELECT sc.id, c.channel_code, sc.gain " +
                        "FROM session_channels sc " +
                        "JOIN channels c ON sc.channel_id = c.channel_id AND c.profile_id = sc.profile_id " +
                        "WHERE sc.session_id = ?",
                        rs -> new ScRow(rs.getLong("id"), rs.getString("channel_code"), rs.getDouble("gain")),
                        row[0]);

                for (ScRow scRow : scRows) {
                    gains.put(scRow.code(), scRow.gain());

                    List<long[]> hist = db.query(
                            "SELECT value, count FROM session_channel_values WHERE session_channel_id = ? ORDER BY value",
                            r -> new long[]{r.getLong("value"), r.getLong("count")}, scRow.scId());

                    TreeMap<Integer, Long> buckets = merged.computeIfAbsent(scRow.code(), k -> new TreeMap<>());
                    for (long[] h : hist) {
                        buckets.merge((int) h[0], h[1], Long::sum);
                    }
                }
            }

            Map<String, OscarChannelHistogram> result = new LinkedHashMap<>();
            for (Map.Entry<String, TreeMap<Integer, Long>> e : merged.entrySet()) {
                result.put(e.getKey(), new OscarChannelHistogram(e.getValue(), gains.getOrDefault(e.getKey(), 1.0)));
            }
            return result;
        }, Map.of());
    }

    public Optional<Map<String, Integer>> loadSummaryEventCounts(LocalDate date) {
        return findSessionForDate(date).flatMap(this::loadSummaryEventCounts);
    }

    public Optional<Map<String, Integer>> loadSummaryEventCounts(OscarSessionIndexEntry indexEntry) {
        return loadSession(indexEntry)
                .flatMap(s -> Optional.ofNullable(s.eventCounts().isEmpty() ? null : s.eventCounts()));
    }

    // ── EDF stubs (Phase 2: blob decompression not yet implemented) ───────────

    public Optional<OscarEdfPaths> edfPathsForDate(LocalDate date) {
        return Optional.empty();
    }

    public Optional<OscarEdfPaths> edfPathsForSession(OscarSessionIndexEntry entry) {
        return Optional.empty();
    }

    public Optional<DeviceEventResult> loadEvents(Path evePath) {
        return Optional.empty();
    }

    public Optional<DeviceEventResult> loadEventsByDate(LocalDate date) {
        return Optional.empty();
    }

    public Optional<WaveformResult> loadPldWaveform(LocalDate date) {
        return Optional.empty();
    }

    // ─────────────────────────────────────────────────────────────────────────

    public OscarProperties properties() {
        return properties;
    }
}
