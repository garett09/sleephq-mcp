package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sleephq.fetch")
public record SleepHqFetchProperties(int parallelism) {

    public SleepHqFetchProperties {
        if (parallelism < 1) {
            parallelism = 8;
        }
    }
}
