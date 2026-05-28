package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OscarDeviceResolverTest {

    @Test
    void resolveDeviceFolder_expandsLeadingTildeSlash() throws IOException {
        Path home = Path.of(System.getProperty("user.home"));
        Path base = Files.createTempDirectory(home, "oscar-device-resolver-");
        try {
            String deviceFolderName = "ResMed_TEST";
            Path deviceDir = Files.createDirectories(base.resolve(deviceFolderName));
            String relativeBase = home.relativize(base).toString().replace('\\', '/');

            OscarProperties props = new OscarProperties(
                    "~/" + relativeBase,
                    null,
                    deviceFolderName,
                    null);
            OscarDeviceResolver resolver = new OscarDeviceResolver(props);

            assertThat(resolver.resolveDeviceFolder()).contains(deviceDir);
        } finally {
            deleteRecursively(base);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
