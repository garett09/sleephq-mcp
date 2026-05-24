package com.adriangarett.sleephqmcp.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class McpResponses {

    private static final Logger log = LoggerFactory.getLogger(McpResponses.class);

    private McpResponses() {
    }

    /**
     * Run the supplier and return its String result. On any thrown exception, return a
     * structured {@link McpError} as JSON so the LLM can read the failure cleanly.
     */
    public static String safe(Supplier<String> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return errorJson(McpError.fatal(e.getMessage(), Map.of("kind", "validation")));
        } catch (Exception e) {
            log.warn("MCP remote call failed", e);
            return errorJson(McpError.fatal(
                    "Upstream request failed",
                    Map.of("kind", "remote", "exception", e.getClass().getSimpleName())));
        }
    }

    public static String errorJson(McpError error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error.error());
        body.put("status", error.status());
        if (error.details() != null && !error.details().isEmpty()) {
            body.put("details", error.details());
        }
        return JsonApi.toJsonString(body);
    }
}
