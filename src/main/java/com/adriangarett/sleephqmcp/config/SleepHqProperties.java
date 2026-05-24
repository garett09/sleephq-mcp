package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sleephq")
public record SleepHqProperties(
        String baseUrl,
        String clientId,
        String clientSecret
) {
}
