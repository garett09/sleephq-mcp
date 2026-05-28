package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class OscarDeviceResolver {

    private final OscarProperties properties;

    public OscarDeviceResolver(OscarProperties properties) {
        this.properties = properties;
    }

    public Optional<Path> resolveDeviceFolder() {
        Path base = resolveDataPath();
        if (base == null || !Files.isDirectory(base)) {
            return Optional.empty();
        }
        String configured = properties.deviceFolder();
        if (configured != null && !configured.isBlank()) {
            Path device = base.resolve(configured);
            return Files.isDirectory(device) ? Optional.of(device) : Optional.empty();
        }
        Optional<Path> fromMachinesXml = resolveFromMachinesXml(base);
        if (fromMachinesXml.isPresent()) {
            return fromMachinesXml;
        }
        String profile = properties.profileName();
        if (profile == null || profile.isBlank()) {
            return Optional.empty();
        }
        Path profileDir = base.resolve("Profiles").resolve(profile);
        if (!Files.isDirectory(profileDir)) {
            return Optional.empty();
        }
        try (Stream<Path> dirs = Files.list(profileDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("ResMed_"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<Path> resolveFromMachinesXml(Path dataRoot) {
        String profile = properties.profileName();
        if (profile == null || profile.isBlank()) {
            return Optional.empty();
        }
        Path machinesXml = dataRoot.resolve("Profiles").resolve(profile).resolve("machines.xml");
        if (!Files.isRegularFile(machinesXml)) {
            return Optional.empty();
        }
        try {
            String xml = Files.readString(machinesXml);
            int resmedIdx = xml.indexOf("ResMed");
            if (resmedIdx < 0) {
                return Optional.empty();
            }
            int serialStart = xml.indexOf("serial=\"", resmedIdx);
            if (serialStart < 0) {
                return Optional.empty();
            }
            serialStart += 8;
            int serialEnd = xml.indexOf('"', serialStart);
            if (serialEnd < 0) {
                return Optional.empty();
            }
            String serial = xml.substring(serialStart, serialEnd);
            Path device = dataRoot.resolve("Profiles").resolve(profile).resolve("ResMed_" + serial);
            return Files.isDirectory(device) ? Optional.of(device) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Path resolveDataPath() {
        String path = properties.dataPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (path.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), path.substring(2));
        }
        return Path.of(path);
    }
}
