package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * MCP HTTP surface authentication. When {@code allow-anonymous} is false, {@code api-key}
 * must be non-blank and clients must send {@code X-SleepHQ-MCP-Key}.
 */
@ConfigurationProperties(prefix = "sleephq.mcp")
public record McpAuthProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("false") boolean allowAnonymous
) {
}
