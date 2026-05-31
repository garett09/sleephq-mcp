package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.NightSessionFile;
import com.adriangarett.sleephqmcp.support.BinaryDownloadSupport;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.NightDateGrouping;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Resolves a night's session files from the SleepHQ API and downloads bytes from S3 on demand. */
@Component
public class ApiNightFileSource implements NightFileSource {

    private static final int MAX_PAGES = 10;
    private static final int FILES_PER_PAGE = 100;
    private static final int IMPORTS_PER_PAGE = 25;
    private static final long MIN_O2_FILE_SIZE = 20_000L;
    private static final long MAX_O2_FILE_SIZE = 200_000L;

    private final SleepHqClient client;
    private final RestClient s3RestClient;
    private final ClinicalContextProperties clinical;

    public ApiNightFileSource(SleepHqClient client,
                              @Qualifier("s3RestClient") RestClient s3RestClient,
                              ClinicalContextProperties clinical) {
        this.client = client;
        this.s3RestClient = s3RestClient;
        this.clinical = clinical;
    }

    @Override
    public String label() {
        return "sleephq_api";
    }

    @Override
    public boolean available() {
        String t = clinical.defaultTeamId();
        return t != null && !t.isBlank();
    }

    @Override
    public List<NightSessionFile> cpapSessions(String date) {
        String teamId = clinical.defaultTeamId();
        if (teamId == null || teamId.isBlank()) {
            return List.of();
        }
        String clean = NightDateGrouping.cleanDate(date);
        List<NightSessionFile> out = new ArrayList<>();
        int page = 1;
        while (page <= MAX_PAGES) {
            JsonNode data = JsonApi.parse(client.listTeamFiles(teamId, page, FILES_PER_PAGE)).path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            for (JsonNode item : data) {
                JsonNode attrs = item.path("attributes");
                String name = attrs.path("name").asText("");
                String path = attrs.path("path").asText("");
                if (!name.toLowerCase(Locale.ROOT).endsWith("_pld.edf")) {
                    continue;
                }
                if (!NightDateGrouping.datalogPathMatches(path, date)
                        && !name.toLowerCase(Locale.ROOT).contains(clean)) {
                    continue;
                }
                String fileId = item.path("id").asText();
                out.add(new NightSessionFile(name, NightDateGrouping.parseStamp(name),
                        () -> downloadFile(fileId), fileId));
            }
            if (data.size() < FILES_PER_PAGE) {
                break;
            }
            page++;
        }
        out.sort(Comparator.comparing(NightSessionFile::start,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    @Override
    public List<NightSessionFile> o2Sessions(String date) {
        String teamId = clinical.defaultTeamId();
        String o2Mid = clinical.defaultO2MachineId();
        if (teamId == null || teamId.isBlank() || o2Mid == null || o2Mid.isBlank()) {
            return List.of();
        }
        List<NightSessionFile> out = new ArrayList<>();
        int page = 1;
        while (page <= MAX_PAGES) {
            JsonNode data = JsonApi.parse(client.listImports(teamId, page, IMPORTS_PER_PAGE)).path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            for (JsonNode item : data) {
                JsonNode attrs = item.path("attributes");
                if (!o2Mid.equals(attrs.path("machine_id").asText(""))) {
                    continue;
                }
                long size = attrs.path("file_size").asLong(0);
                if (size < MIN_O2_FILE_SIZE || size > MAX_O2_FILE_SIZE) {
                    continue;
                }
                LocalDateTime start = NightDateGrouping.parseStamp(attrs.path("name").asText(""));
                if (start == null) {
                    start = parseCreatedAt(attrs.path("created_at").asText(""));
                }
                if (!NightDateGrouping.inNoonWindow(start, date)) {
                    continue;
                }
                String fileId = singleFileId(item.path("id").asText());
                if (fileId != null) {
                    final LocalDateTime finalStart = start;
                    out.add(new NightSessionFile(attrs.path("name").asText(""), finalStart,
                            () -> downloadFile(fileId), fileId));
                }
            }
            if (data.size() < IMPORTS_PER_PAGE) {
                break;
            }
            page++;
        }
        out.sort(Comparator.comparing(NightSessionFile::start,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private static LocalDateTime parseCreatedAt(String created) {
        if (created.length() >= 19) {
            try {
                return LocalDateTime.parse(created.substring(0, 19).replace(' ', 'T'));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private String singleFileId(String importId) {
        JsonNode data = JsonApi.parse(client.listImportFiles(importId, 1, 10)).path("data");
        return data.isArray() && !data.isEmpty() ? data.get(0).path("id").asText() : null;
    }

    private byte[] downloadFile(String fileId) {
        JsonNode attrs = JsonApi.attributes(JsonApi.parse(client.getImportFile(fileId)));
        String url = attrs.path("download_url").asText(null);
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("download_url missing or not HTTPS for file " + fileId);
        }
        return BinaryDownloadSupport.download(s3RestClient, URI.create(url), fileId);
    }
}
