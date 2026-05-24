package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sleephq")
public record SleepHqProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        @DefaultValue("read") String oauthScope
) {
}
