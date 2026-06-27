package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class P3CorsSecurityGuardTest {

    @Test
    void p3CorsAllowsCredentialsOnlyForExactConfiguredOrigin() {
        CorsConfiguration configuration = corsConfiguration();

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("http://localhost:4200")).isEqualTo("http://localhost:4200");
        assertThat(configuration.checkOrigin("http://localhost:4300")).isNull();
        assertThat(configuration.checkOrigin("http://localhost:4200")).isNotEqualTo("*");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.checkHttpMethod(HttpMethod.OPTIONS)).contains(HttpMethod.OPTIONS);
    }

    private CorsConfiguration corsConfiguration() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:4200", "*", "http://localhost:4200/path"));
        properties.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        properties.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token"));
        CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(properties);
        var request = org.springframework.mock.http.server.reactive.MockServerHttpRequest.options("/api/v1/secure-probe")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .build();
        return source.getCorsConfiguration(org.springframework.mock.web.server.MockServerWebExchange.from(request));
    }
}
