package com.adriangarett.sleephqmcp.support;

import java.util.Map;

public record McpError(String error, String status, Map<String, Object> details) {

    public static McpError fatal(String message) {
        return new McpError(message, "fatal", Map.of());
    }

    public static McpError fatal(String message, Map<String, Object> details) {
        return new McpError(message, "fatal", details);
    }

    public static McpError retry(String message) {
        return new McpError(message, "retry", Map.of());
    }
}
