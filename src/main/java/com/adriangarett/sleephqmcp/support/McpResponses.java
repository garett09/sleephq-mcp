package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.owasp.encoder.Encode;
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
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof JsonProcessingException jpe) {
                String safeDetail = jpe.getOriginalMessage() != null ? jpe.getOriginalMessage() : jpe.getMessage();
                log.warn("MCP JSON assembly failed: {}", Encode.forJava(String.valueOf(safeDetail)), e);
                return errorJson(McpError.fatal(
                        "Could not assemble JSON response",
                        Map.of("kind", "json")));
            }
            String message = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : "Upstream request failed";
            if (isExpectedMissingNight(message)) {
                log.warn("MCP call failed: {}", Encode.forJava(message));
            } else {
                log.warn("MCP call failed: {}", Encode.forJava(message), e);
            }
            return errorJson(McpError.fatal(message,
                    Map.of("kind", "remote", "exception", e.getClass().getSimpleName())));
        } catch (Exception e) {
            log.warn("MCP remote call failed: {}", Encode.forJava(String.valueOf(e.getMessage())), e);
            return errorJson(McpError.fatal(
                    "Upstream request failed",
                    Map.of("kind", "remote", "exception", e.getClass().getSimpleName())));
        }
    }

    private static boolean isExpectedMissingNight(String message) {
        return message.startsWith("No CPAP machine_date for date=")
                || message.startsWith("No O2 machine_date for date=");
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
