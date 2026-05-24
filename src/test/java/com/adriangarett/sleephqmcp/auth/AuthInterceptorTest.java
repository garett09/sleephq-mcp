package com.adriangarett.sleephqmcp.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private TokenManager tokenManager;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenManager = mock(TokenManager.class);
        interceptor = new AuthInterceptor(tokenManager);
    }

    @Test
    void skipsAuthForOAuthEndpoint() throws Exception {
        HttpRequest req = mockRequest("/oauth/token");
        ClientHttpResponse okResp = mockResponse(HttpStatus.OK);
        ClientHttpRequestExecution exec = (r, b) -> okResp;

        ClientHttpResponse response = interceptor.intercept(req, new byte[0], exec);

        assertThat(response).isSameAs(okResp);
        assertThat(req.getHeaders().getFirst("Authorization")).isNull();
    }

    @Test
    void addsBearerForApiEndpoint() throws Exception {
        HttpRequest req = mockRequest("/api/v1/teams");
        when(tokenManager.getValidToken()).thenReturn("token-1");
        ClientHttpResponse okResp = mockResponse(HttpStatus.OK);
        ClientHttpRequestExecution exec = (r, b) -> okResp;

        interceptor.intercept(req, new byte[0], exec);

        assertThat(req.getHeaders().getFirst("Authorization")).isEqualTo("Bearer token-1");
    }

    @Test
    void retriesOnceOn401() throws Exception {
        HttpRequest req = mockRequest("/api/v1/teams");
        when(tokenManager.getValidToken()).thenReturn("token-1", "token-2");

        ClientHttpResponse unauthorized = mockResponse(HttpStatus.UNAUTHORIZED);
        ClientHttpResponse ok = mockResponse(HttpStatus.OK);
        ClientHttpRequestExecution exec = mock(ClientHttpRequestExecution.class);
        when(exec.execute(any(), any())).thenReturn(unauthorized, ok);

        ClientHttpResponse response = interceptor.intercept(req, new byte[0], exec);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(tokenManager).invalidate();
        verify(tokenManager, times(2)).getValidToken();
        verify(exec, times(2)).execute(any(), any());
    }

    private static HttpRequest mockRequest(String path) {
        HttpRequest req = mock(HttpRequest.class);
        when(req.getURI()).thenReturn(URI.create("https://sleephq.com" + path));
        when(req.getHeaders()).thenReturn(new HttpHeaders());
        return req;
    }

    private static ClientHttpResponse mockResponse(HttpStatus status) throws Exception {
        ClientHttpResponse resp = mock(ClientHttpResponse.class);
        when(resp.getStatusCode()).thenReturn(status);
        when(resp.getHeaders()).thenReturn(new HttpHeaders());
        when(resp.getBody()).thenReturn(emptyStream());
        return resp;
    }

    private static InputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }
}
