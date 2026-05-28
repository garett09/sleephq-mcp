package com.adriangarett.sleephqmcp.auth;

import com.adriangarett.sleephqmcp.config.SleepHqProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TokenManagerTest {

    @Test
    void authenticate_sendsPasswordGrantWithUsernameAndPassword() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sleephq.com/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://sleephq.com/api/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=password"),
                        org.hamcrest.Matchers.containsString("client_id=cid"),
                        org.hamcrest.Matchers.containsString("client_secret=csec"),
                        org.hamcrest.Matchers.containsString("username=cid"),
                        org.hamcrest.Matchers.containsString("password=csec"),
                        org.hamcrest.Matchers.containsString("scope=read"))))
                .andRespond(withSuccess("""
                        {"access_token":"tok","token_type":"Bearer","expires_in":3600,"created_at":1710000000}
                        """, MediaType.APPLICATION_JSON));

        TokenManager manager = new TokenManager(builder.build(),
                new SleepHqProperties("https://sleephq.com/api", "cid", "csec", "read"));
        assertThat(manager.getValidToken()).isEqualTo("tok");
        server.verify();
    }
}
