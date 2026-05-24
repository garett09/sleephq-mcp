package com.adriangarett.sleephqmcp.security;

import com.adriangarett.sleephqmcp.config.McpAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Protects {@code /mcp} with a shared secret header when anonymous access is disabled.
 */
@Component
public class McpApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String MCP_API_KEY_HEADER = "X-SleepHQ-MCP-Key";

    private final McpAuthProperties mcpAuthProperties;

    public McpApiKeyAuthFilter(McpAuthProperties mcpAuthProperties) {
        this.mcpAuthProperties = mcpAuthProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isMcpRequestPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (mcpAuthProperties.allowAnonymous()) {
            filterChain.doFilter(request, response);
            return;
        }
        String configured = mcpAuthProperties.apiKey();
        if (configured == null || configured.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "MCP API key is not configured; set SLEEPHQ_MCP_API_KEY or enable sleephq.mcp.allow-anonymous=true");
            return;
        }
        String presented = request.getHeader(MCP_API_KEY_HEADER);
        if (!constantTimeEquals(presented, configured)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    "Header realm=\"sleephq-mcp\", header=\"" + MCP_API_KEY_HEADER + "\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing MCP API key");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isMcpRequestPath(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        return path.startsWith("/mcp");
    }

    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        int max = Math.max(left.length, right.length);
        return MessageDigest.isEqual(Arrays.copyOf(left, max), Arrays.copyOf(right, max));
    }
}
