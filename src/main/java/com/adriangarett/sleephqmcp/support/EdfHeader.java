package com.adriangarett.sleephqmcp.support;

import java.time.LocalDateTime;

/**
 * Parsed EDF/EDF+ fixed header and per-signal metadata (no data records).
 */
public record EdfHeader(
        LocalDateTime startDatetime,
        int headerBytes,
        int nRecords,
        double recordDurationSeconds,
        int signalCount,
        String[] labels,
        int[] samplesPerRecord
) {
}
