package com.adriangarett.sleephqmcp.domain;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * One night's session file from any source. {@code bytes} is lazy so resolution does not eagerly
 * download or read every file. {@code start} may be null when the filename has no parseable stamp.
 * {@code fileId} is set for SleepHQ API sessions (import file id); null for local mirror paths.
 */
public record NightSessionFile(String name, LocalDateTime start, Supplier<byte[]> bytes, String fileId) {

    public NightSessionFile(String name, LocalDateTime start, Supplier<byte[]> bytes) {
        this(name, start, bytes, null);
    }
}
