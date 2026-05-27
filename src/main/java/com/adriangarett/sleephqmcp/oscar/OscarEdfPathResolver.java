package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.OscarEdfPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class OscarEdfPathResolver {

    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.BASIC_ISO_DATE;

    private OscarEdfPathResolver() {}

    public static OscarEdfPaths resolve(Path deviceFolder, LocalDate calendarDate) throws IOException {
        return resolve(deviceFolder, List.of(calendarDate));
    }

    /**
     * Tries each candidate date prefix (e.g. session start vs calendar end night) under every DATALOG root.
     */
    public static OscarEdfPaths resolve(Path deviceFolder, List<LocalDate> candidateDates) throws IOException {
        List<Path> datalogRoots = datalogRoots(deviceFolder);
        if (datalogRoots.isEmpty() || candidateDates.isEmpty()) {
            return new OscarEdfPaths(Optional.empty(), Optional.empty(), Optional.empty());
        }
        Set<LocalDate> uniqueDates = new LinkedHashSet<>(candidateDates);
        Optional<Path> eve = Optional.empty();
        Optional<Path> pld = Optional.empty();
        Optional<Path> brp = Optional.empty();
        for (LocalDate date : uniqueDates) {
            for (Path datalog : datalogRoots) {
                OscarEdfPaths found = resolveUnderDatalog(datalog, date);
                eve = pickLatest(eve, found.eve());
                pld = pickLatest(pld, found.pld());
                brp = pickLatest(brp, found.brp());
            }
            if (eve.isPresent() || pld.isPresent() || brp.isPresent()) {
                break;
            }
        }
        return new OscarEdfPaths(eve, pld, brp);
    }

    private static List<Path> datalogRoots(Path deviceFolder) {
        List<Path> roots = new ArrayList<>(2);
        Path backup = deviceFolder.resolve("Backup").resolve("DATALOG");
        Path active = deviceFolder.resolve("DATALOG");
        if (Files.isDirectory(backup)) {
            roots.add(backup);
        }
        if (Files.isDirectory(active)) {
            roots.add(active);
        }
        return roots;
    }

    private static OscarEdfPaths resolveUnderDatalog(Path datalog, LocalDate calendarDate) throws IOException {
        String prefix = DATE_PREFIX.format(calendarDate);
        Path yearDir = datalog.resolve(Integer.toString(calendarDate.getYear()));
        if (!Files.isDirectory(yearDir)) {
            try (Stream<Path> years = Files.list(datalog)) {
                yearDir = years.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().equals(Integer.toString(calendarDate.getYear())))
                        .findFirst()
                        .orElse(yearDir);
            }
        }
        if (!Files.isDirectory(yearDir)) {
            return new OscarEdfPaths(Optional.empty(), Optional.empty(), Optional.empty());
        }
        Optional<Path> eve = findLatest(yearDir, prefix, "_EVE.edf");
        Optional<Path> pld = findLatest(yearDir, prefix, "_PLD.edf");
        Optional<Path> brp = findLatest(yearDir, prefix, "_BRP.edf");
        return new OscarEdfPaths(eve, pld, brp);
    }

    private static Optional<Path> pickLatest(Optional<Path> current, Optional<Path> candidate) {
        if (candidate.isEmpty()) {
            return current;
        }
        if (current.isEmpty()) {
            return candidate;
        }
        return current.get().compareTo(candidate.get()) >= 0 ? current : candidate;
    }

    private static Optional<Path> findLatest(Path dir, String datePrefix, String suffix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(datePrefix) && name.endsWith(suffix);
                    })
                    .max(Path::compareTo);
        }
    }
}
