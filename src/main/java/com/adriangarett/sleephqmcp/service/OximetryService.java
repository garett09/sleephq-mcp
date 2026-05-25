package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.OximetryResult;
import com.adriangarett.sleephqmcp.support.BinaryDownloadSupport;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.O2ImportResolver;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.adriangarett.sleephqmcp.support.ViatomSessionParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Service
public class OximetryService {

    private final SleepHqClient sleepHqClient;
    private final RestClient s3RestClient;
    private final ClinicalContextProperties clinical;

    public OximetryService(SleepHqClient sleepHqClient,
                           @Qualifier("s3RestClient") RestClient s3RestClient,
                           ClinicalContextProperties clinical) {
        this.sleepHqClient = sleepHqClient;
        this.s3RestClient = s3RestClient;
        this.clinical = clinical;
    }

    public String getOximetry(String fileId, int maxSeconds) {
        String fileJson = sleepHqClient.getImportFile(fileId);
        JsonNode attrs = JsonApi.attributes(JsonApi.parse(fileJson));
        String downloadUrl = requireDownloadUrl(attrs, fileId);
        String filename = attrs.path("name").asText("");

        URI uri = URI.create(downloadUrl);
        byte[] bytes = BinaryDownloadSupport.download(s3RestClient, uri, fileId);
        OximetryResult result = ViatomSessionParser.parse(bytes, filename, maxSeconds);
        return serialize(result);
    }

    public String getOximetryByDate(String date, String o2MachineId, int maxSeconds) {
        String teamId = clinical.defaultTeamId();
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("SLEEPHQ_TEAM_ID is required to resolve O2 imports by date");
        }
        String o2Mid = resolveO2MachineId(o2MachineId);
        String fileId = O2ImportResolver.resolveFileIdByDate(sleepHqClient, teamId, o2Mid, date);
        return getOximetry(fileId, maxSeconds);
    }

    private String resolveO2MachineId(String o2MachineId) {
        if (o2MachineId != null && !o2MachineId.isBlank()) {
            return SleepHqPathParams.requireResourceId(o2MachineId, "o2MachineId");
        }
        if (clinical.defaultO2MachineId() == null || clinical.defaultO2MachineId().isBlank()) {
            throw new IllegalArgumentException("Required o2MachineId is missing and no default SLEEPHQ_O2_MACHINE_ID is configured");
        }
        return SleepHqPathParams.requireResourceId(clinical.defaultO2MachineId(), "SLEEPHQ_O2_MACHINE_ID");
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

    private static String serialize(OximetryResult result) {
        try {
            return JsonApi.mapper().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize oximetry result", e);
        }
    }
}
