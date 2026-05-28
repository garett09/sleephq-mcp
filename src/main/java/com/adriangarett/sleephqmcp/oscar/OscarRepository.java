package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.domain.OscarEdfPaths;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.support.EdfAnnotationParser;
import com.adriangarett.sleephqmcp.support.EdfParser;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OscarRepository {

    private static final Logger log = LoggerFactory.getLogger(OscarRepository.class);

    private final OscarProperties properties;
    private final OscarDeviceResolver deviceResolver;
    private volatile OscarSummariesIndex summariesIndex;
    private volatile Path cachedDeviceFolder;
    private volatile String lastIndexError;

    public OscarRepository(OscarProperties properties) {
        this.properties = properties;
        this.deviceResolver = new OscarDeviceResolver(properties);
    }

    public boolean isConfigured() {
        return deviceResolver.resolveDeviceFolder().isPresent();
    }

    public boolean isReachable() {
        Optional<Path> device = deviceResolver.resolveDeviceFolder();
        if (device.isEmpty()) {
            return false;
        }
        Path summaries = device.get().resolve("Summaries");
        Path sessionsInfo = device.get().resolve("Sessions.info");
        return Files.isDirectory(summaries) && Files.isRegularFile(sessionsInfo);
    }

    public Optional<Path> deviceFolder() {
        return deviceResolver.resolveDeviceFolder();
    }

    public OscarSummariesIndex summariesIndex() {
        Path device = deviceResolver.resolveDeviceFolder().orElse(null);
        if (device == null) {
            return new OscarSummariesIndex(List.of());
        }
        if (summariesIndex != null && device.equals(cachedDeviceFolder)) {
            return summariesIndex;
        }
        try {
            summariesIndex = OscarSummariesIndex.load(device);
            cachedDeviceFolder = device;
            lastIndexError = null;
            return summariesIndex;
        } catch (Exception e) {
            log.warn("Failed to load Summaries.xml.gz: {}", e.getMessage());
            lastIndexError = e.getMessage();
            return new OscarSummariesIndex(List.of());
        }
    }

    public Optional<LocalDate> getLastSessionDate() {
        return summariesIndex().sessions().stream()
                .filter(OscarSessionIndexEntry::enabled)
                .map(s -> s.lastInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                .max(Comparator.naturalOrder());
    }

    /** Returns the error message from the last failed index load, or {@code null} if the last load succeeded. */
    public String getLastIndexError() {
        return lastIndexError;
    }

    public Optional<OscarSessionIndexEntry> findSessionForDate(LocalDate date) {
        return summariesIndex().findPrimarySessionForDate(date, ZoneId.systemDefault());
    }

    public List<OscarSessionIndexEntry> findSessionsInRange(LocalDate start, LocalDate end) {
        List<OscarSessionIndexEntry> result = new ArrayList<>();
        for (OscarSessionIndexEntry entry : summariesIndex().sessions()) {
            if (!entry.enabled()) {
                continue;
            }
            LocalDate sessionStart = entry.firstInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (!sessionStart.isBefore(start) && !sessionStart.isAfter(end)) {
                result.add(entry);
            }
        }
        return result;
    }

    public Optional<OscarSession> loadSession(LocalDate date) {
        return findSessionForDate(date).flatMap(this::loadSession);
    }

    public Optional<OscarSession> loadSession(OscarSessionIndexEntry indexEntry) {
        Path device = deviceResolver.resolveDeviceFolder().orElse(null);
        if (device == null) {
            return Optional.empty();
        }
        Path summary = device.resolve("Summaries").resolve(indexEntry.summaryFileName());
        if (!Files.isRegularFile(summary)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(summary);
            LocalDate date = indexEntry.firstInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            OscarSession session = OscarSummaryParser.parse(bytes, date, ZoneId.systemDefault());
            if (session.availableChannelIds().isEmpty()) {
                session = OscarSummaryParser.parseHeaderOnly(bytes, date, ZoneId.systemDefault(),
                        indexEntry.channelIds());
            }
            if (session.channels().isEmpty()) {
                Map<Integer, ChannelSummary> scanned =
                        OscarSummaryChannelStats.extract(bytes, session, indexEntry.channelIds());
                if (!scanned.isEmpty()) {
                    session = new OscarSession(
                            session.date(),
                            session.sessionId(),
                            session.startMs(),
                            session.durationSeconds(),
                            scanned,
                            session.availableChannelIds().isEmpty()
                                    ? List.copyOf(scanned.keySet())
                                    : session.availableChannelIds());
                }
            }
            return Optional.of(session);
        } catch (Exception e) {
            log.warn("Failed to parse summary {}: {}", summary, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<OscarSummaryHeaderParser.SummaryHeader> loadSummaryHeader(OscarSessionIndexEntry session) {
        Path device = deviceResolver.resolveDeviceFolder().orElse(null);
        if (device == null) {
            return Optional.empty();
        }
        Path summary = device.resolve("Summaries").resolve(session.summaryFileName());
        if (!Files.isRegularFile(summary)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(summary);
            return Optional.of(OscarSummaryHeaderParser.parse(bytes));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<OscarEdfPaths> edfPathsForDate(LocalDate date) {
        Path device = deviceResolver.resolveDeviceFolder().orElse(null);
        if (device == null) {
            return Optional.empty();
        }
        try {
            OscarEdfPaths direct = OscarEdfPathResolver.resolve(device, date);
            if (hasEdfData(direct)) {
                return Optional.of(direct);
            }
            return findSessionForDate(date)
                    .map(entry -> edfPathsForSession(device, entry))
                    .filter(OscarRepository::hasEdfData);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<OscarEdfPaths> edfPathsForSession(OscarSessionIndexEntry entry) {
        Path device = deviceResolver.resolveDeviceFolder().orElse(null);
        if (device == null) {
            return Optional.empty();
        }
        try {
            OscarEdfPaths paths = edfPathsForSession(device, entry);
            return hasEdfData(paths) ? Optional.of(paths) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static OscarEdfPaths edfPathsForSession(Path device, OscarSessionIndexEntry entry) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate startDate = entry.firstInstant().atZone(zone).toLocalDate();
        LocalDate endDate = entry.lastInstant().atZone(zone).toLocalDate();
        List<LocalDate> candidates = new ArrayList<>();
        candidates.add(startDate);
        if (!endDate.equals(startDate)) {
            candidates.add(endDate);
        }
        try {
            return OscarEdfPathResolver.resolve(device, candidates);
        } catch (IOException e) {
            return new OscarEdfPaths(Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    private static boolean hasEdfData(OscarEdfPaths paths) {
        return paths.eve().isPresent() || paths.pld().isPresent() || paths.brp().isPresent();
    }

    public Optional<DeviceEventResult> loadEvents(Path evePath) {
        if (evePath == null || !Files.isRegularFile(evePath)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(evePath);
            return Optional.of(EdfAnnotationParser.parse(bytes, evePath.getFileName().toString()));
        } catch (Exception e) {
            log.warn("Failed to parse EVE {}: {}", evePath, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DeviceEventResult> loadEventsByDate(LocalDate date) {
        return edfPathsForDate(date).flatMap(OscarEdfPaths::eve).flatMap(this::loadEvents);
    }

    public Optional<Map<String, Integer>> loadSummaryEventCounts(LocalDate date) {
        return findSessionForDate(date).flatMap(this::loadSummaryEventCounts);
    }

    public Optional<Map<String, Integer>> loadSummaryEventCounts(OscarSessionIndexEntry indexEntry) {
        Path device = deviceResolver.resolveDeviceFolder().orElse(null);
        if (device == null) {
            return Optional.empty();
        }
        Optional<OscarSession> session = loadSession(indexEntry);
        Map<String, Integer> fromSession = session.map(OscarSummaryEventCounts::fromSession).orElse(Map.of());
        if (!fromSession.isEmpty()) {
            return Optional.of(fromSession);
        }
        Path summary = device.resolve("Summaries").resolve(indexEntry.summaryFileName());
        if (!Files.isRegularFile(summary)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(summary);
            Map<String, Integer> scanned = OscarSummaryEventCounts.extract(
                    bytes, session.orElse(null), indexEntry.channelIds());
            return scanned.isEmpty() ? Optional.empty() : Optional.of(scanned);
        } catch (Exception e) {
            log.warn("Failed to read summary event counts {}: {}", summary, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<WaveformResult> loadPldWaveform(LocalDate date) {
        return edfPathsForDate(date).flatMap(OscarEdfPaths::pld).flatMap(this::loadWaveform);
    }

    private Optional<WaveformResult> loadWaveform(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return Optional.of(EdfParser.parse(bytes, 0, Integer.MAX_VALUE));
        } catch (Exception e) {
            log.warn("Failed to parse EDF {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    public OscarProperties properties() {
        return properties;
    }
}
