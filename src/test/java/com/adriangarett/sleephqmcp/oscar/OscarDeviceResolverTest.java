package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class OscarDeviceResolverTest {

    @Test
    void findsDbFileInDataRoot(@TempDir Path root) throws IOException {
        Path db = root.resolve("oscar.db");
        Files.createFile(db);
        assertThat(new OscarDeviceResolver(props(root.toString())).resolveDbFile()).contains(db);
    }

    @Test
    void returnsEmptyWhenDbFileMissing(@TempDir Path root) {
        assertThat(new OscarDeviceResolver(props(root.toString())).resolveDbFile()).isEmpty();
    }

    @Test
    void returnsEmptyWhenDataPathBlank() {
        assertThat(new OscarDeviceResolver(props("")).resolveDbFile()).isEmpty();
    }

    @Test
    void expandsHomeDirectory(@TempDir Path root) throws IOException {
        Files.createFile(root.resolve("oscar.db"));
        assertThat(new OscarDeviceResolver(props(root.toString())).resolveDbFile()).isPresent();
    }

    private static OscarProperties props(String dataPath) {
        return new OscarProperties(false, dataPath, "", "",
                new OscarProperties.Analysis(95, 120, 20, 100, 5));
    }
}
