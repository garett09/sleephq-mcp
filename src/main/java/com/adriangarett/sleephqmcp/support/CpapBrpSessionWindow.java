package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.service.WaveformService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * CPAP BRP.edf session bounds for clipping Apple Health sleep segments to therapy night.
 */
public final class CpapBrpSessionWindow {

    public record Bounds(Instant start, Instant end) {
    }

    private CpapBrpSessionWindow() {
    }

    public static Optional<Bounds> tryResolve(WaveformService waveformService,
                                              String teamId,
                                              String calendarDate,
                                              Integer cpapClockAdjustSeconds) {
        if (waveformService == null || calendarDate == null || calendarDate.isBlank()) {
            return Optional.empty();
        }
        try {
            ApneaScanResult scan = waveformService.loadScanByDate(teamId, calendarDate, cpapClockAdjustSeconds);
            if (scan == null || scan.startDatetime() == null || scan.startDatetime().length() < 19) {
                return Optional.empty();
            }
            LocalDateTime sessionStart = LocalDateTime.parse(scan.startDatetime().substring(0, 19));
            Instant start = sessionStart.toInstant(ZoneOffset.UTC);
            Instant end = start.plusSeconds(Math.max(0L, (long) scan.durationSeconds()));
            if (!end.isAfter(start)) {
                return Optional.empty();
            }
            return Optional.of(new Bounds(start, end));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
