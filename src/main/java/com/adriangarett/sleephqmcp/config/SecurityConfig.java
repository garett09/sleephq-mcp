package com.adriangarett.sleephqmcp.config;

import com.adriangarett.sleephqmcp.security.McpApiKeyAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public SecurityConfig(McpAuthProperties mcpAuthProperties) {
        if (!mcpAuthProperties.allowAnonymous()
                && (mcpAuthProperties.apiKey() == null || mcpAuthProperties.apiKey().isBlank())) {
            throw new IllegalStateException(
                    "MCP authentication is required: set environment variable SLEEPHQ_MCP_API_KEY, "
                            + "or set sleephq.mcp.allow-anonymous=true for local development only.");
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, McpApiKeyAuthFilter mcpApiKeyAuthFilter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(mcpApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
