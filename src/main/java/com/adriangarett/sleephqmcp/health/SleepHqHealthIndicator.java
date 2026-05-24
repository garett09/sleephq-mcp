package com.adriangarett.sleephqmcp.health;

import com.adriangarett.sleephqmcp.auth.TokenManager;
import com.adriangarett.sleephqmcp.config.SleepHqProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Reports UP if SleepHQ credentials are configured. Does NOT make a network call —
 * health endpoints get hit on every probe; we don't want to thrash the SleepHQ token endpoint.
 */
@Component("sleepHq")
public class SleepHqHealthIndicator implements HealthIndicator {

    private final SleepHqProperties properties;
    private final TokenManager tokenManager;

    public SleepHqHealthIndicator(SleepHqProperties properties, TokenManager tokenManager) {
        this.properties = properties;
        this.tokenManager = tokenManager;
    }

    @Override
    public Health health() {
        Health.Builder builder = credentialsPresent() ? Health.up() : Health.down();
        builder.withDetail("baseUrl", properties.baseUrl());
        builder.withDetail("credentialsConfigured", credentialsPresent());
        builder.withDetail("tokenAuthenticated", tokenManager.isAuthenticated());
        Instant expiresAt = tokenManager.expiresAt();
        builder.withDetail("tokenExpiresAt", expiresAt == null ? "not_yet_authenticated" : expiresAt.toString());
        return builder.build();
    }

    private boolean credentialsPresent() {
        return notBlank(properties.clientId()) && notBlank(properties.clientSecret());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
