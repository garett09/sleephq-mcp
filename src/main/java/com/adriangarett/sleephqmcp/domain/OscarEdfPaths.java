package com.adriangarett.sleephqmcp.domain;

import java.nio.file.Path;
import java.util.Optional;

public record OscarEdfPaths(
        Optional<Path> eve,
        Optional<Path> pld,
        Optional<Path> brp
) {}
