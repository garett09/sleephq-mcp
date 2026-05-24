package com.adriangarett.sleephqmcp.auth;

import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthInterceptor implements ClientHttpRequestInterceptor {

    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (isOAuthEndpoint(request)) {
            return execution.execute(request, body);
        }

        request.getHeaders().setBearerAuth(tokenManager.getValidToken());
        ClientHttpResponse response = execution.execute(request, body);

        if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            response.close();
            tokenManager.invalidate();
            request.getHeaders().setBearerAuth(tokenManager.getValidToken());
            return execution.execute(request, body);
        }
        return response;
    }

    private boolean isOAuthEndpoint(HttpRequest request) {
        return request.getURI().getPath().startsWith("/oauth/");
    }
}
