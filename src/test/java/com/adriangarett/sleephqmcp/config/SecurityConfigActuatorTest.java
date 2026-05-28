package com.adriangarett.sleephqmcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sleephq.mcp.api-key=test-mcp-secret",
        "sleephq.mcp.allow-anonymous=false",
        "sleephq.base-url=https://sleephq.com",
        "sleephq.client-id=test-id",
        "sleephq.client-secret=test-secret"
})
class SecurityConfigActuatorTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void actuatorHealth_permitted() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void actuatorEnv_deniedByDefault() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
    }
}
