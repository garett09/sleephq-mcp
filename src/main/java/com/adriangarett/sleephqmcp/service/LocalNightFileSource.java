package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.SleepHqLocalProperties;
import com.adriangarett.sleephqmcp.domain.NightSessionFile;
import com.adriangarett.sleephqmcp.support.NightDateGrouping;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Reads the local SleepHQ mirror (RESMED_DATA / SLEEPHQ_O2_RING) produced by sleephq_download.py. */
@Component
public class LocalNightFileSource implements NightFileSource {

    private final SleepHqLocalProperties props;

    public LocalNightFileSource(SleepHqLocalProperties props) {
        this.props = props;
    }

    @Override
    public String label() {
        return "local";
    }

    @Override
    public boolean available() {
        String p = props.dataPath();
        return p != null && !p.isBlank() && Files.isDirectory(Path.of(p));
    }

    @Override
    public List<NightSessionFile> cpapSessions(String date) {
        String clean = NightDateGrouping.cleanDate(date);
        if (props.dataPath() == null || props.dataPath().isBlank()) {
            return List.of();
        }
        Path folder = Path.of(props.dataPath(), "DATALOG", clean);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<NightSessionFile> out = new ArrayList<>();
        try (Stream<Path> entries = Files.list(folder)) {
            entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_pld.edf"))
                    .forEach(p -> out.add(toSession(p)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(startComparator());
        return out;
    }

    @Override
    public List<NightSessionFile> o2Sessions(String date) {
        if (props.o2Path() == null || props.o2Path().isBlank()) {
            return List.of();
        }
        Path dir = Path.of(props.o2Path());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<NightSessionFile> out = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) {
                    return;
                }
                LocalDateTime start = NightDateGrouping.parseStamp(name);
                if (NightDateGrouping.inNoonWindow(start, date)) {
                    out.add(new NightSessionFile(name, start, () -> read(p)));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(startComparator());
        return out;
    }

    private NightSessionFile toSession(Path p) {
        String name = p.getFileName().toString();
        return new NightSessionFile(name, NightDateGrouping.parseStamp(name), () -> read(p));
    }

    private static Comparator<NightSessionFile> startComparator() {
        return Comparator.comparing(NightSessionFile::start,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static byte[] read(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
