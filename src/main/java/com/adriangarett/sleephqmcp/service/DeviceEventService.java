package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.support.CpapClockAlignment;
import com.adriangarett.sleephqmcp.support.EdfAnnotationParser;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.PhaseTiming;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.OptionalInt;

@Service
public class DeviceEventService {

    private final SleepHqCacheFacade cacheFacade;
    private final ClinicalContextProperties clinical;
    private final MachineDateTimeOffsetLoader machineDateTimeOffsetLoader;
    private final PhaseTiming phaseTiming;

    public DeviceEventService(SleepHqCacheFacade cacheFacade, ClinicalContextProperties clinical,
                              MachineDateTimeOffsetLoader machineDateTimeOffsetLoader, PhaseTiming phaseTiming) {
        this.cacheFacade = cacheFacade;
        this.clinical = clinical;
        this.machineDateTimeOffsetLoader = machineDateTimeOffsetLoader;
        this.phaseTiming = phaseTiming;
    }

    public String getDeviceEvents(String fileId) {
        return getDeviceEvents(fileId, null);
    }

    public String getDeviceEvents(String fileId, Integer cpapClockAdjustSeconds) {
        return getDeviceEvents(fileId, cpapClockAdjustSeconds, OptionalInt.empty());
    }

    public String getDeviceEventsByDate(String teamId, String date) {
        return getDeviceEventsByDate(teamId, date, null);
    }

    public String getDeviceEventsByDate(String teamId, String date, Integer cpapClockAdjustSeconds) {
        String resolvedTeamId = teamId != null && !teamId.isBlank() ? teamId : clinical.defaultTeamId();
        if (resolvedTeamId == null || resolvedTeamId.isBlank()) {
            throw new IllegalArgumentException("Required teamId is missing and no default SLEEPHQ_TEAM_ID is configured");
        }
        String fileId = cacheFacade.resolveTeamFileByDate(resolvedTeamId, date, "eve.edf");
        OptionalInt machineDateOffset = machineDateTimeOffsetLoader.loadForCpapDate(date, null);
        return getDeviceEvents(fileId, cpapClockAdjustSeconds, machineDateOffset);
    }

    public String getDeviceEvents(String fileId, Integer cpapClockAdjustSeconds, OptionalInt machineDateOffset) {
        Map<String, Long> phases = PhaseTiming.newPhaseMap();
        String cacheKey = fileId + "|" + cpapClockAdjustSeconds + "|" + machineDateOffset.orElse(Integer.MIN_VALUE);
        String json = cacheFacade.getCachedDeviceEventsJson(cacheKey, () -> loadDeviceEventsJson(fileId, cpapClockAdjustSeconds, machineDateOffset, phases));
        phaseTiming.logSummary("get-device-events", phases);
        return json;
    }

    private String loadDeviceEventsJson(String fileId, Integer cpapClockAdjustSeconds, OptionalInt machineDateOffset,
                                        Map<String, Long> phases) {
        String fileJson;
        try (PhaseTiming.Scope ignored = phaseTiming.start("get-device-events", "metadata")) {
            fileJson = cacheFacade.getImportFile(fileId);
            phases.put("metadata_ms", ignored.elapsedMillis());
        }
        JsonNode attrs = JsonApi.attributes(JsonApi.parse(fileJson));
        String downloadUrl = requireDownloadUrl(attrs, fileId);
        String filename = attrs.path("name").asText("");

        URI uri = URI.create(downloadUrl);
        byte[] bytes;
        try (PhaseTiming.Scope ignored = phaseTiming.start("get-device-events", "download")) {
            bytes = cacheFacade.downloadEdf(uri, fileId);
            phases.put("download_ms", ignored.elapsedMillis());
        }
        DeviceEventResult result;
        try (PhaseTiming.Scope ignored = phaseTiming.start("get-device-events", "parse")) {
            result = EdfAnnotationParser.parse(bytes, filename);
            phases.put("parse_ms", ignored.elapsedMillis());
        }
        CpapClockAlignment.CpapClockAdjustResolution resolution =
                CpapClockAlignment.resolveAdjust(clinical, cpapClockAdjustSeconds, machineDateOffset);
        return CpapClockAlignment.serializeWithAlignment(
                CpapClockAlignment.alignDeviceEvents(result, resolution.adjustSeconds()), resolution);
    }

    private static String requireDownloadUrl(JsonNode attrs, String fileId) {
        String downloadUrl = attrs.path("download_url").asText(null);
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("download_url missing for file " + fileId);
        }
        if (!downloadUrl.startsWith("https://")) {
            throw new IllegalArgumentException("download_url is not HTTPS for file " + fileId);
        }
        return downloadUrl;
    }
}
