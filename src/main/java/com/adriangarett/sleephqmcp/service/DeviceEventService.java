package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.support.BinaryDownloadSupport;
import com.adriangarett.sleephqmcp.support.EdfAnnotationParser;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.TeamFileResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Service
public class DeviceEventService {

    private final SleepHqClient sleepHqClient;
    private final RestClient s3RestClient;
    private final ClinicalContextProperties clinical;

    public DeviceEventService(SleepHqClient sleepHqClient,
                              @Qualifier("s3RestClient") RestClient s3RestClient,
                              ClinicalContextProperties clinical) {
        this.sleepHqClient = sleepHqClient;
        this.s3RestClient = s3RestClient;
        this.clinical = clinical;
    }

    public String getDeviceEvents(String fileId) {
        String fileJson = sleepHqClient.getImportFile(fileId);
        JsonNode attrs = JsonApi.attributes(JsonApi.parse(fileJson));
        String downloadUrl = requireDownloadUrl(attrs, fileId);
        String filename = attrs.path("name").asText("");

        URI uri = URI.create(downloadUrl);
        byte[] bytes = BinaryDownloadSupport.download(s3RestClient, uri, fileId);
        DeviceEventResult result = EdfAnnotationParser.parse(bytes, filename);
        return serialize(result);
    }

    public String getDeviceEventsByDate(String teamId, String date) {
        String resolvedTeamId = teamId != null && !teamId.isBlank() ? teamId : clinical.defaultTeamId();
        if (resolvedTeamId == null || resolvedTeamId.isBlank()) {
            throw new IllegalArgumentException("Required teamId is missing and no default SLEEPHQ_TEAM_ID is configured");
        }
        String fileId = TeamFileResolver.resolveByDate(sleepHqClient, resolvedTeamId, date, "eve.edf");
        return getDeviceEvents(fileId);
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

    private static String serialize(DeviceEventResult result) {
        try {
            return JsonApi.mapper().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize device event result", e);
        }
    }
}
