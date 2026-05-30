package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.SleepHqLocalProperties;
import com.adriangarett.sleephqmcp.domain.NightSessionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalNightFileSourceTest {

    @Test
    void cpapSessions_readsDatalogFolder_forNight(@TempDir Path root) throws Exception {
        Path o2 = Files.createTempDirectory("o2");
        Path datalog = Files.createDirectories(root.resolve("DATALOG/20260417"));
        // Post-midnight session filed under the 17th folder (ResMed grouping).
        Files.write(datalog.resolve("20260418_015119_PLD.edf"), new byte[]{1, 2, 3});
        Files.write(datalog.resolve("20260417_223000_PLD.edf"), new byte[]{4, 5, 6});
        Files.write(datalog.resolve("20260417_223000_BRP.edf"), new byte[]{7}); // not PLD -> ignored

        LocalNightFileSource src = new LocalNightFileSource(
                new SleepHqLocalProperties(root.toString(), o2.toString()));

        List<NightSessionFile> files = src.cpapSessions("2026-04-17");

        assertThat(files).extracting(NightSessionFile::name)
                .containsExactly("20260417_223000_PLD.edf", "20260418_015119_PLD.edf"); // sorted by start
        assertThat(files.get(0).bytes().get()).containsExactly(4, 5, 6);
    }

    @Test
    void o2Sessions_appliesNoonSplit_onFlatDir(@TempDir Path o2) throws Exception {
        Path data = Files.createTempDirectory("data");
        Files.write(o2.resolve("20260517230000-1721"), new byte[]{1});   // night of 17th -> IN
        Files.write(o2.resolve("20260518010000-1721"), new byte[]{2});   // post-midnight -> IN
        Files.write(o2.resolve("20260518130000-1721"), new byte[]{3});   // after noon next day -> OUT

        LocalNightFileSource src = new LocalNightFileSource(
                new SleepHqLocalProperties(data.toString(), o2.toString()));

        assertThat(src.o2Sessions("2026-05-17")).extracting(NightSessionFile::name)
                .containsExactly("20260517230000-1721", "20260518010000-1721");
    }

    @Test
    void available_falseWhenPathBlankOrMissing() {
        assertThat(new LocalNightFileSource(new SleepHqLocalProperties("", "")).available()).isFalse();
        assertThat(new LocalNightFileSource(new SleepHqLocalProperties("/no/such/dir", "")).available()).isFalse();
    }
}
