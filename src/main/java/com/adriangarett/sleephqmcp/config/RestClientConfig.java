package com.adriangarett.sleephqmcp.config;

import com.adriangarett.sleephqmcp.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient sleepHqRestClient(SleepHqProperties properties, AuthInterceptor authInterceptor) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestInterceptor(authInterceptor)
                .build();
    }

    @Bean
    public RestClient tokenRestClient(SleepHqProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    @Qualifier("s3RestClient")
    public RestClient s3RestClient() {
        // No base URL, no auth interceptor — S3 signed URLs are self-authenticating absolute URIs
        return RestClient.builder().build();
    }
}
