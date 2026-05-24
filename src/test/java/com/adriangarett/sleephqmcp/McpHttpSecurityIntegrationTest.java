package com.adriangarett.sleephqmcp;

import com.adriangarett.sleephqmcp.security.McpApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sleephq.mcp.api-key=test-mcp-secret",
        "sleephq.mcp.allow-anonymous=false",
        "sleephq.client-id=test-id",
        "sleephq.client-secret=test-secret"
})
class McpHttpSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mcpPost_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.parseMediaType("text/event-stream"))
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpPost_withApiKey_isNotUnauthorized() throws Exception {
        int status = mockMvc.perform(post("/mcp")
                        .header(McpApiKeyAuthFilter.MCP_API_KEY_HEADER, "test-mcp-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.parseMediaType("text/event-stream"))
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(503);
    }
}
