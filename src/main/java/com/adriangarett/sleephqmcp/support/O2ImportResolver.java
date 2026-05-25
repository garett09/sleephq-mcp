package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Resolves O2 Ring import file IDs via {@code list-imports} and {@code list-import-files}.
 */
public final class O2ImportResolver {

    private static final long MIN_O2_FILE_SIZE = 20_000L;
    private static final long MAX_O2_FILE_SIZE = 200_000L;
    private static final int MAX_IMPORT_PAGES = 5;
    private static final int IMPORTS_PER_PAGE = 25;

    private O2ImportResolver() {}

    public static String resolveFileIdByDate(SleepHqClient client, String teamId, String o2MachineId, String date) {
        String cleanDate = date.replace("-", "").trim();
        if (cleanDate.length() != 8) {
            throw new IllegalArgumentException("Invalid date format: " + date + ". Expected YYYY-MM-DD.");
        }

        String bestImportId = null;
        long bestSize = Long.MAX_VALUE;

        int page = 1;
        while (page <= MAX_IMPORT_PAGES) {
            String importsJson = client.listImports(teamId, page, IMPORTS_PER_PAGE);
            JsonNode data = JsonApi.parse(importsJson).path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            for (JsonNode item : data) {
                JsonNode attrs = item.path("attributes");
                String machineId = attrs.path("machine_id").asText("");
                if (!o2MachineId.equals(machineId)) {
                    continue;
                }
                long fileSize = attrs.path("file_size").asLong(0);
                if (fileSize < MIN_O2_FILE_SIZE || fileSize > MAX_O2_FILE_SIZE) {
                    continue;
                }
                String name = attrs.path("name").asText("").toLowerCase(Locale.ROOT);
                String createdAt = attrs.path("created_at").asText("");
                boolean dateMatch = name.contains(cleanDate) || createdAt.contains(date);
                if (!dateMatch) {
                    continue;
                }
                if (fileSize < bestSize) {
                    bestSize = fileSize;
                    bestImportId = item.path("id").asText();
                }
            }
            if (data.size() < IMPORTS_PER_PAGE) {
                break;
            }
            page++;
        }

        if (bestImportId == null) {
            throw new IllegalArgumentException(
                    "No O2 import found for date " + date + " (machineId: " + o2MachineId + ", teamId: " + teamId + ")");
        }

        return resolveSingleFileFromImport(client, bestImportId);
    }

    private static String resolveSingleFileFromImport(SleepHqClient client, String importId) {
        String filesJson = client.listImportFiles(importId, 1, 10);
        JsonNode data = JsonApi.parse(filesJson).path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalArgumentException("No files on O2 import " + importId);
        }
        if (data.size() > 1) {
            throw new IllegalArgumentException("Expected one file on O2 import " + importId + " but found " + data.size());
        }
        return data.get(0).path("id").asText();
    }
}
