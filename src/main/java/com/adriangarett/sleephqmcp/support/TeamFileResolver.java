package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Resolves team file IDs from {@code list-files} by calendar date and filename suffix.
 */
public final class TeamFileResolver {

    private static final int MAX_PAGES = 5;
    private static final int PER_PAGE = 100;

    private TeamFileResolver() {}

    public static String resolveByDate(SleepHqClient client, String teamId, String date, String filenameSuffix) {
        String cleanDate = date.replace("-", "").trim();
        if (cleanDate.length() != 8) {
            throw new IllegalArgumentException("Invalid date format: " + date + ". Expected YYYY-MM-DD.");
        }
        String suffix = filenameSuffix.toLowerCase(Locale.ROOT);

        int pageNum = 1;
        while (pageNum <= MAX_PAGES) {
            String filesJson = client.listTeamFiles(teamId, pageNum, PER_PAGE);
            JsonNode root = JsonApi.parse(filesJson);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            for (JsonNode item : data) {
                String name = item.path("attributes").path("name").asText("");
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (lowerName.contains(cleanDate) && lowerName.contains(suffix)) {
                    return item.path("id").asText();
                }
            }
            if (data.size() < PER_PAGE) {
                break;
            }
            pageNum++;
        }
        throw new IllegalArgumentException(
                "No file matching '*" + cleanDate + "*" + suffix + "' for date " + date + " (teamId: " + teamId + ")");
    }
}
