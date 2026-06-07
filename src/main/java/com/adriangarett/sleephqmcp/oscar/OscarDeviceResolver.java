package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class OscarDeviceResolver {

    private final OscarProperties properties;

    public OscarDeviceResolver(OscarProperties properties) {
        this.properties = properties;
    }

    /** Returns the path to oscar.db if it exists under the configured data root. */
    public Optional<Path> resolveDbFile() {
        Path base = resolveDataPath();
        if (base == null || !Files.isDirectory(base)) {
            return Optional.empty();
        }
        Path db = base.resolve("oscar.db");
        return Files.isRegularFile(db) ? Optional.of(db) : Optional.empty();
    }

    private Path resolveDataPath() {
        String path = properties.dataPath();
        if (path == null || path.isBlank()) return null;
        if (path.equals("~")) return Path.of(System.getProperty("user.home"));
        if (path.startsWith("~/")) return Path.of(System.getProperty("user.home"), path.substring(2));
        return Path.of(path);
    }
}
