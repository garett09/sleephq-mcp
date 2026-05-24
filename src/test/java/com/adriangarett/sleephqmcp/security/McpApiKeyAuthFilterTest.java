package com.adriangarett.sleephqmcp.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class McpApiKeyAuthFilterTest {

    @Test
    void pathWithinApplication_stripsContextPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/app");
        request.setRequestURI("/app/mcp");
        assertThat(McpApiKeyAuthFilter.pathWithinApplication(request)).isEqualTo("/mcp");
    }

    @Test
    void isMcpRequestPath_trueForMcpUri() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/mcp");
        assertThat(McpApiKeyAuthFilter.isMcpRequestPath(request)).isTrue();
    }

    @Test
    void constantTimeEquals_matchesEqualStrings() {
        assertThat(McpApiKeyAuthFilter.constantTimeEquals("secret", "secret")).isTrue();
    }

    @Test
    void constantTimeEquals_rejectsMismatched() {
        assertThat(McpApiKeyAuthFilter.constantTimeEquals("secret", "other")).isFalse();
    }

    @Test
    void constantTimeEquals_rejectsNull() {
        assertThat(McpApiKeyAuthFilter.constantTimeEquals(null, "x")).isFalse();
        assertThat(McpApiKeyAuthFilter.constantTimeEquals("x", null)).isFalse();
    }
}
