package com.bct.ngtpa.apiservice.infrastructure.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig {

    @Value("${api.security.require-authentication:true}")
    private boolean requireAuthentication;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            CorsConfigurationSource corsConfigurationSource) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource));

        if (requireAuthentication) {
            log.info("Security: in-process authentication enabled (api.security.require-authentication=true)");
            http.authorizeExchange(auth -> auth
                .pathMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .pathMatchers("/api/v1/internal/**").authenticated()
                .anyExchange().authenticated());
        } else {
            log.warn("Security: in-process authentication DISABLED (api.security.require-authentication=false). "
                    + "Ensure an external auth layer (ingress/gateway) is protecting all business endpoints.");
            http.authorizeExchange(auth -> auth.anyExchange().permitAll());
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowedOrigins = safeAllowedOrigins(corsProperties.getAllowedOrigins());

        if (CollectionUtils.isEmpty(allowedOrigins)) {
            return source;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private List<String> safeAllowedOrigins(List<String> configuredOrigins) {
        if (CollectionUtils.isEmpty(configuredOrigins)) {
            return List.of();
        }
        return configuredOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .filter(origin -> !"*".equals(origin))
                .filter(this::isExactOrigin)
                .toList();
    }

    private boolean isExactOrigin(String origin) {
        try {
            URI uri = new URI(origin);
            return uri.getScheme() != null
                    && uri.getHost() != null
                    && (uri.getRawPath() == null || uri.getRawPath().isBlank())
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return false;
        }
    }
}
