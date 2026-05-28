package com.adriangarett.sleephqmcp.auth;

import com.adriangarett.sleephqmcp.config.SleepHqProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

@Component
public class TokenManager {

    private static final Duration EXPIRY_SAFETY_BUFFER = Duration.ofSeconds(60);

    private final RestClient tokenRestClient;
    private final SleepHqProperties properties;

    private String accessToken;
    private Instant expiresAt;

    public TokenManager(@Qualifier("tokenRestClient") RestClient tokenRestClient,
                        SleepHqProperties properties) {
        this.tokenRestClient = tokenRestClient;
        this.properties = properties;
    }

    public synchronized String getValidToken() {
        if (accessToken == null || isExpired()) {
            authenticate();
        }
        return accessToken;
    }

    public synchronized void invalidate() {
        accessToken = null;
        expiresAt = null;
    }

    public synchronized boolean isAuthenticated() {
        return accessToken != null && !isExpired();
    }

    public synchronized Instant expiresAt() {
        return expiresAt;
    }

    private boolean isExpired() {
        return expiresAt == null || Instant.now().isAfter(expiresAt.minus(EXPIRY_SAFETY_BUFFER));
    }

    private void authenticate() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("username", properties.clientId());
        form.add("password", properties.clientSecret());
        form.add("scope", properties.oauthScope());

        TokenResponse response = tokenRestClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("SleepHQ /oauth/token returned no access_token");
        }
        this.accessToken = response.accessToken();
        this.expiresAt = Instant.ofEpochSecond(response.createdAt()).plusSeconds(response.expiresIn());
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("created_at") long createdAt
    ) {
    }
}
