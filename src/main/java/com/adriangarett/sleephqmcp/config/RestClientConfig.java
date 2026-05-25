package com.adriangarett.sleephqmcp.config;

import com.adriangarett.sleephqmcp.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient sleepHqRestClient(SleepHqProperties properties, SleepHqHttpProperties httpProperties,
                                        AuthInterceptor authInterceptor) {
        JdkClientHttpRequestFactory factory = HttpClientFactory.requestFactory(
                httpProperties.connectTimeout(), httpProperties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .requestInterceptor(authInterceptor)
                .build();
    }

    @Bean
    public RestClient tokenRestClient(SleepHqProperties properties, SleepHqHttpProperties httpProperties) {
        JdkClientHttpRequestFactory factory = HttpClientFactory.requestFactory(
                httpProperties.connectTimeout(), httpProperties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    @Qualifier("s3RestClient")
    public RestClient s3RestClient(SleepHqHttpProperties httpProperties) {
        JdkClientHttpRequestFactory factory = HttpClientFactory.requestFactory(
                httpProperties.connectTimeout(), httpProperties.s3ReadTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
