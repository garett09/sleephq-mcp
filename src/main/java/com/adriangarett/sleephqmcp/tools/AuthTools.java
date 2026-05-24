package com.adriangarett.sleephqmcp.tools;

import com.adriangarett.sleephqmcp.auth.TokenManager;
import com.adriangarett.sleephqmcp.client.SleepHqClient;
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

    public AuthTools(SleepHqClient client, TokenManager tokenManager) {
        this.client = client;
        this.tokenManager = tokenManager;
    }

    @McpTool(name = "who-am-i",
            description = "Return the authenticated SleepHQ user (GET /api/v1/me). Use this to verify credentials are wired up.")
    public String whoAmI() {
        return McpResponses.safe(client::getMe);
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
