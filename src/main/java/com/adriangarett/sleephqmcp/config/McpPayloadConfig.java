package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SleepHqPayloadProperties.class)
public class McpPayloadConfig {
}
