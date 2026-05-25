package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.client.SleepHqClient;

/**
 * Resolves team file IDs from {@code list-files} by calendar date and filename suffix.
 */
public final class TeamFileResolver {

    private TeamFileResolver() {}

    public static String resolveByDate(SleepHqClient client, String teamId, String date, String filenameSuffix) {
        return TeamFileResolverSupport.resolveUncached(client, teamId, date, filenameSuffix);
    }
}
