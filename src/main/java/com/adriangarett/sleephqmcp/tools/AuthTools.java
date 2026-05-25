package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.auth.TokenManager;
import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.config.ClinicalDefaultsSupport;
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

    public AuthTools(SleepHqClient client, TokenManager tokenManager, ClinicalContextProperties clinical) {
        this.client = client;
        this.tokenManager = tokenManager;
        this.clinical = clinical;
    }

    @McpTool(name = "who-am-i",
            description = "Return the authenticated SleepHQ user (GET /api/v1/me). Use this to verify credentials are wired up.")
    public String whoAmI() {
        return McpResponses.safe(client::getMe);
    }

    @McpTool(name = "get-configured-defaults",
            description = "Show SLEEPHQ_TEAM_ID, SLEEPHQ_CPAP_MACHINE_ID, and SLEEPHQ_O2_MACHINE_ID as loaded by the server (non-secret). Use after changing .env to confirm cpap_machine_id is the AirSense id from list-machines, not the team id.")
    public String getConfiguredDefaults() {
        return JsonApi.toJsonString(ClinicalDefaultsSupport.configuredDefaultsBody(clinical));
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
