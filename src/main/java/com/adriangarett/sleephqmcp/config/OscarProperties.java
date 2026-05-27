package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oscar")
public record OscarProperties(
        String dataPath,
        String profileName,
        String deviceFolder,
        Analysis analysis
) {
    public record Analysis(
            int percentile,
            int correlationWindowSeconds,
            int maxNotableMoments,
            int maxTimedEvents,
            int maxNearbyEventsPerMoment
    ) {
        public Analysis {
            if (percentile <= 0) {
                percentile = 95;
            }
            if (correlationWindowSeconds <= 0) {
                correlationWindowSeconds = 120;
            }
            if (maxNotableMoments <= 0) {
                maxNotableMoments = 20;
            }
            if (maxTimedEvents <= 0) {
                maxTimedEvents = 100;
            }
            if (maxNearbyEventsPerMoment <= 0) {
                maxNearbyEventsPerMoment = 5;
            }
        }
    }
}
