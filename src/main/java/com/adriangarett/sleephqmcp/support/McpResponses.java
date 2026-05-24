package com.adriangarett.sleephqmcp.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class McpResponses {

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
            return errorJson(McpError.fatal(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
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
