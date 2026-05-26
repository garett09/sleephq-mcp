package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties;
import com.adriangarett.sleephqmcp.service.DeviceEventService;
import com.adriangarett.sleephqmcp.service.OximetryService;
import com.adriangarett.sleephqmcp.service.WaveformService;
import com.adriangarett.sleephqmcp.support.McpResponses;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class WaveformTools {

    private final WaveformService waveformService;
    private final DeviceEventService deviceEventService;
    private final OximetryService oximetryService;
    private final SleepHqPayloadProperties payload;

    public WaveformTools(WaveformService waveformService,
                         DeviceEventService deviceEventService,
                         OximetryService oximetryService,
                         SleepHqPayloadProperties payload) {
        this.waveformService = waveformService;
        this.deviceEventService = deviceEventService;
        this.oximetryService = oximetryService;
        this.payload = payload;
    }

    @McpTool(
            name = "get-waveform",
            description = "Downloads and parses an EDF device file from SleepHQ, returning time-series channel data. " +
                    "Requires fileId from list-files or list-import-files. " +
                    "Returns: filename, start_datetime (ISO-8601), duration_seconds (full recording), and channels[] " +
                    "each with label, sample_rate (Hz), unit, and samples[]. " +
                    "ResMed PLD.edf channels: Flow, Pressure, Leak, Snore, FlowLimitation at 25 Hz. " +
                    "Sample window capped at maxMinutes (server default from sleephq.mcp.payload.waveform-default-max-minutes). " +
                    "Arrays downsampled per sleephq.mcp.payload.waveform-max-samples-per-channel (see sample_count_original). " +
                    "Prefer scan-apnea-events for event lists. Supports startMinute offset."
    )
    public String getWaveform(
            @McpToolParam(description = "File ID from list-files or list-import-files", required = true)
            String fileId,
            @McpToolParam(description = "Start minute within the night (default 0)", required = false)
            Integer startMinute,
            @McpToolParam(description = "Max minutes of samples per channel (server default when omitted)", required = false)
            Integer maxMinutes,
            @McpToolParam(description = "Seconds added to CPAP EDF wall-clock timestamps (overrides SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS)", required = false)
            Integer cpapClockAdjustSeconds) {
        return McpResponses.safe(() -> {
            String id = SleepHqPathParams.requireResourceId(fileId, "fileId");
            int startMin = startMinute == null ? 0 : startMinute;
            if (startMin < 0) {
                throw new IllegalArgumentException("startMinute must be non-negative");
            }
            int minutes = resolveWaveformMaxMinutes(maxMinutes);
            return waveformService.getWaveform(id, startMin * 60, minutes * 60, cpapClockAdjustSeconds);
        });
    }

    @McpTool(
            name = "get-waveform-by-date",
            description = "Resolves the BRP.edf file ID for a calendar date, downloads and parses it, and returns the segment's channel data. " +
                    "Avoids file ID hunting. Returns the same structure as get-waveform. " +
                    "Date format YYYY-MM-DD. Default maxMinutes from server payload config. Prefer scan-apnea-events for titration."
    )
    public String getWaveformByDate(
            @McpToolParam(description = "Calendar date (YYYY-MM-DD)", required = true)
            String date,
            @McpToolParam(description = "Start minute within the night (default 0)", required = false)
            Integer startMinute,
            @McpToolParam(description = "Max minutes of samples per channel (server default when omitted)", required = false)
            Integer maxMinutes,
            @McpToolParam(description = "Team ID. Defaults to SLEEPHQ_TEAM_ID.", required = false)
            String teamId,
            @McpToolParam(description = "Seconds added to CPAP EDF wall-clock timestamps (overrides SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS)", required = false)
            Integer cpapClockAdjustSeconds) {
        return McpResponses.safe(() -> {
            String cleanDate = SleepHqPathParams.requireCalendarDate(date, "date");
            int startMin = startMinute == null ? 0 : startMinute;
            if (startMin < 0) {
                throw new IllegalArgumentException("startMinute must be non-negative");
            }
            int minutes = resolveWaveformMaxMinutes(maxMinutes);
            return waveformService.getWaveformByDate(teamId, cleanDate, startMin * 60, minutes * 60, cpapClockAdjustSeconds);
        });
    }

    private int resolveWaveformMaxMinutes(Integer maxMinutes) {
        int minutes = maxMinutes == null ? payload.waveformDefaultMaxMinutes() : maxMinutes;
        int cap = payload.waveformMaxMinutesCap();
        if (minutes < 1 || minutes > cap) {
            throw new IllegalArgumentException("maxMinutes must be between 1 and " + cap);
        }
        return minutes;
    }

    @McpTool(
            name = "scan-apnea-events",
            description = "Downloads a BRP.edf file (by file ID or calendar date) and runs a server-side detection algorithm " +
                    "over the full respiration flow channel. Returns a list of all detected apnea/severe-hypopnea flow drops " +
                    "(periods where flow absolute amplitude drops below threshold for >= minDurationSeconds). " +
                    "Threshold defaults to 0.15 L/s. minDurationSeconds defaults to 10 seconds. " +
                    "Full-night server-side flow analysis — equivalent to waveform for event lists and reconciliation with EVE; "
                    + "does not require get-waveform-by-date for High-confidence mechanism when aligned with device events."
    )
    public String scanApneaEvents(
            @McpToolParam(description = "File ID of the BRP.edf file. Optional if date is provided.", required = false)
            String fileId,
            @McpToolParam(description = "Calendar date (YYYY-MM-DD). Optional if fileId is provided.", required = false)
            String date,
            @McpToolParam(description = "Team ID. Defaults to SLEEPHQ_TEAM_ID.", required = false)
            String teamId,
            @McpToolParam(description = "Flow rate envelope threshold (L/s) to classify apnea (default 0.15)", required = false)
            Double threshold,
            @McpToolParam(description = "Minimum duration in seconds to classify as apnea (default 10)", required = false)
            Integer minDurationSeconds,
            @McpToolParam(description = "Seconds added to CPAP EDF wall-clock timestamps (overrides SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS)", required = false)
            Integer cpapClockAdjustSeconds) {
        return McpResponses.safe(() -> {
            String cleanDate = date != null && !date.isBlank() ? SleepHqPathParams.requireCalendarDate(date, "date") : null;
            String cleanFileId = fileId != null && !fileId.isBlank() ? SleepHqPathParams.requireResourceId(fileId, "fileId") : null;
            if (cleanFileId == null && cleanDate == null) {
                throw new IllegalArgumentException("Either fileId or date must be provided");
            }
            if (threshold != null && threshold <= 0) {
                throw new IllegalArgumentException("threshold must be positive");
            }
            if (minDurationSeconds != null && minDurationSeconds < 5) {
                throw new IllegalArgumentException("minDurationSeconds must be at least 5");
            }
            return waveformService.scanApneaEvents(cleanFileId, teamId, cleanDate, threshold, minDurationSeconds,
                    cpapClockAdjustSeconds);
        });
    }

    @McpTool(
            name = "get-device-events",
            description = "Downloads a ResMed EVE.edf file and parses device-reported respiratory events from EDF+ annotations. "
                    + "Returns timestamps, durations, raw labels, and OSCAR-style codes (OA, CA, H, A, FL, etc.). "
                    + "This is the CPAP device's own event log — not the flow-derived scan-apnea-events algorithm."
    )
    public String getDeviceEvents(
            @McpToolParam(description = "File ID of the EVE.edf file. Optional if date is provided.", required = false)
            String fileId,
            @McpToolParam(description = "Calendar date (YYYY-MM-DD). Optional if fileId is provided.", required = false)
            String date,
            @McpToolParam(description = "Team ID. Defaults to SLEEPHQ_TEAM_ID.", required = false)
            String teamId,
            @McpToolParam(description = "Seconds added to CPAP EDF wall-clock timestamps (overrides SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS)", required = false)
            Integer cpapClockAdjustSeconds) {
        return McpResponses.safe(() -> {
            String cleanDate = date != null && !date.isBlank() ? SleepHqPathParams.requireCalendarDate(date, "date") : null;
            String cleanFileId = fileId != null && !fileId.isBlank() ? SleepHqPathParams.requireResourceId(fileId, "fileId") : null;
            if (cleanFileId == null && cleanDate == null) {
                throw new IllegalArgumentException("Either fileId or date must be provided");
            }
            if (cleanFileId != null) {
                return deviceEventService.getDeviceEvents(cleanFileId, cpapClockAdjustSeconds);
            }
            return deviceEventService.getDeviceEventsByDate(teamId, cleanDate, cpapClockAdjustSeconds);
        });
    }

    @McpTool(
            name = "get-o2-oximetry",
            description = "Downloads a Viatom O2 Ring binary session via list-imports (not EDF). "
                    + "O2Ring S (0x0301): 1 s samples; classic VLD3: ~4 s. Recommended maxMinutes from "
                    + "get-comparison mcp_payload_hints.o2_recommended_max_minutes (full night can exceed 1M chars). "
                    + "Nightly averages: get-combined-night-by-date."
    )
    public String getO2Oximetry(
            @McpToolParam(description = "File ID from list-import-files. Optional if date is provided.", required = false)
            String fileId,
            @McpToolParam(description = "Calendar date (YYYY-MM-DD). Optional if fileId is provided.", required = false)
            String date,
            @McpToolParam(description = "O2 machine ID. Defaults to SLEEPHQ_O2_MACHINE_ID.", required = false)
            String o2MachineId,
            @McpToolParam(description = "Max minutes of samples to return (default full session, max 720)", required = false)
            Integer maxMinutes) {
        return McpResponses.safe(() -> {
            String cleanDate = date != null && !date.isBlank() ? SleepHqPathParams.requireCalendarDate(date, "date") : null;
            String cleanFileId = fileId != null && !fileId.isBlank() ? SleepHqPathParams.requireResourceId(fileId, "fileId") : null;
            if (cleanFileId == null && cleanDate == null) {
                throw new IllegalArgumentException("Either fileId or date must be provided");
            }
            int maxSec = maxMinutes == null ? Integer.MAX_VALUE / 2 : maxMinutes * 60;
            if (maxMinutes != null && (maxMinutes < 1 || maxMinutes > 720)) {
                throw new IllegalArgumentException("maxMinutes must be between 1 and 720");
            }
            if (cleanFileId != null) {
                return oximetryService.getOximetry(cleanFileId, maxSec);
            }
            return oximetryService.getOximetryByDate(cleanDate, o2MachineId, maxSec);
        });
    }
}
