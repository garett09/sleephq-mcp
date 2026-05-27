package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.OscarEdfPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OscarEdfPathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolve_findsEdfByDatePrefix() throws Exception {
        Path yearDir = tempDir.resolve("Backup").resolve("DATALOG").resolve("2026");
        Files.createDirectories(yearDir);
        Path eve = yearDir.resolve("20260520_210912_EVE.edf");
        Path pld = yearDir.resolve("20260520_210920_PLD.edf");
        Files.writeString(eve, "0");
        Files.writeString(pld, "0");

        OscarEdfPaths paths = OscarEdfPathResolver.resolve(tempDir, LocalDate.parse("2026-05-20"));
        assertThat(paths.eve()).isPresent();
        assertThat(paths.pld()).isPresent();
        assertThat(paths.eve().get().getFileName().toString()).endsWith("_EVE.edf");
    }

    @Test
    void resolve_calendarEndDate_findsFilesPrefixedWithSessionStart() throws Exception {
        Path yearDir = tempDir.resolve("Backup").resolve("DATALOG").resolve("2026");
        Files.createDirectories(yearDir);
        Files.writeString(yearDir.resolve("20260520_210912_EVE.edf"), "0");
        Files.writeString(yearDir.resolve("20260520_210920_PLD.edf"), "0");

        OscarEdfPaths paths = OscarEdfPathResolver.resolve(
                tempDir,
                List.of(LocalDate.parse("2026-05-21"), LocalDate.parse("2026-05-20")));
        assertThat(paths.eve()).isPresent();
        assertThat(paths.pld()).isPresent();
    }
}
