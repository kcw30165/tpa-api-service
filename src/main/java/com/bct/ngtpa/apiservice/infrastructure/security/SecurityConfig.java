package com.bct.ngtpa.apiservice.infrastructure.security;

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

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH"); // , "DELETE", "OPTIONS"
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Accept");

    /**
     * Controls whether in-process authentication is enforced.
     *
     * <p>Set to {@code false} when authentication is handled externally (e.g. K8s ingress
     * controller, API gateway, or service mesh mTLS). In that case all traffic reaching this
     * service is assumed to be already authenticated at the network boundary.
     *
     * <p>Controlled via env var {@code API_SECURITY_REQUIRE_AUTHENTICATION=false} in K8s
     * ConfigMap/Deployment. Defaults to {@code true} so local developer workstations and CI
     * always enforce auth unless explicitly opted out.
     */
    @Value("${api.security.require-authentication:true}")
    private boolean requireAuthentication;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
            CorsConfigurationSource corsConfigurationSource) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource));

        if (requireAuthentication) {
            // HTTP Basic is auto-configured by Spring Boot when spring-security is on the classpath.
            // Credentials for local development are defined in application-local.yml.
            // Replace with OAuth2/OIDC resource server config when an auth server is available.
            log.info("Security: in-process authentication enabled (api.security.require-authentication=true)");
            http.authorizeExchange(auth -> auth
                // Allow CORS preflight without authentication so browsers can negotiate origins.
                .pathMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                // All other requests — including all business API endpoints — require authentication.
                .anyExchange().authenticated());
        } else {
            // Authentication is delegated to the external layer (K8s ingress / API gateway).
            // All traffic reaching this service is trusted at the network boundary.
            log.warn("Security: in-process authentication DISABLED (api.security.require-authentication=false). "
                    + "Ensure an external auth layer (ingress/gateway) is protecting all business endpoints.");
            http.authorizeExchange(auth -> auth.anyExchange().permitAll());
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        if (CollectionUtils.isEmpty(corsProperties.getAllowedOrigins())) {
            return source;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setMaxAge(3600L);

        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
