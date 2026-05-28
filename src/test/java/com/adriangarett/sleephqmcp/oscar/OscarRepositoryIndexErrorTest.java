package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OscarRepositoryIndexErrorTest {

    @TempDir
    Path tempDir;

    @Test
    void summariesIndex_whenSummariesXmlGzCorrupt_setsLastIndexError() throws IOException {
        Path summaries = Files.createDirectory(tempDir.resolve("Summaries"));
        Files.writeString(tempDir.resolve("Sessions.info"), "");
        Path xmlGz = tempDir.resolve("Summaries.xml.gz");
        Files.writeString(xmlGz, "not a real gzip file");

        OscarProperties props = new OscarProperties(tempDir.getParent().toString(), null, tempDir.getFileName().toString(), null);
        OscarRepository repo = new OscarRepository(props);

        OscarSummariesIndex index = repo.summariesIndex();

        assertThat(index.sessions()).isEmpty();
        assertThat(repo.isReachable()).isTrue();
        assertThat(repo.getLastIndexError()).isNotNull();
        assertThat(repo.getLastIndexError()).isNotBlank();
    }

    @Test
    void summariesIndex_whenLoadSucceeds_clearsLastIndexError() throws IOException {
        Files.createDirectory(tempDir.resolve("Summaries"));
        Files.writeString(tempDir.resolve("Sessions.info"), "");

        OscarProperties props = new OscarProperties(tempDir.getParent().toString(), null, tempDir.getFileName().toString(), null);
        OscarRepository repo = new OscarRepository(props);

        repo.summariesIndex();

        assertThat(repo.summariesIndex()).isNotNull();
    }
}
