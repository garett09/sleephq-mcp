package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.auth.TokenManager;
import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.config.ClinicalDefaultsSupport;
import com.adriangarett.sleephqmcp.config.SleepHqPayloadProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.McpResponses;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuthTools {

    private final SleepHqClient client;
    private final TokenManager tokenManager;
    private final ClinicalContextProperties clinical;
    private final SleepHqPayloadProperties payload;

    public AuthTools(
            SleepHqClient client,
            TokenManager tokenManager,
            ClinicalContextProperties clinical,
            SleepHqPayloadProperties payload) {
        this.client = client;
        this.tokenManager = tokenManager;
        this.clinical = clinical;
        this.payload = payload;
    }

    @McpTool(name = "who-am-i",
            description = "Return the authenticated SleepHQ user (GET /api/v1/me). Use this to verify credentials are wired up.")
    public String whoAmI() {
        return McpResponses.safe(client::getMe);
    }

    @McpTool(name = "get-configured-defaults",
            description = "Show SLEEPHQ_TEAM_ID, SLEEPHQ_CPAP_MACHINE_ID, SLEEPHQ_O2_MACHINE_ID, and mcp_payload_hints "
                    + "(waveform_default_max_minutes, waveform_max_minutes_cap, etc.) as loaded by the server (non-secret). "
                    + "Call after changing .env; restart ./run.sh for new limits. Omit maxMinutes on get-waveform-by-date to use waveform_default_max_minutes.")
    public String getConfiguredDefaults() {
        return JsonApi.toJsonString(ClinicalDefaultsSupport.configuredDefaultsBody(clinical, payload));
    }

    @McpTool(name = "get-token-status",
            description = "Report whether the cached SleepHQ bearer token is valid and when it expires.")
    public String getTokenStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", tokenManager.isAuthenticated());
        Instant expiresAt = tokenManager.expiresAt();
        body.put("expires_at", expiresAt == null ? null : expiresAt.toString());
        body.put("now", Instant.now().toString());
        return JsonApi.toJsonString(body);
    }
}
