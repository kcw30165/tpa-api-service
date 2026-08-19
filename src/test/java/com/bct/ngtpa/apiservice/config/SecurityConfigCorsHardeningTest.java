package com.bct.ngtpa.apiservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

class SecurityConfigCorsHardeningTest {

  @Test
  void corsAllowsCredentialsForConfiguredExactOrigin() {
    CorsConfiguration configuration =
        corsConfigurationFor("http://localhost:4200", HttpMethod.POST);

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkOrigin("http://localhost:4200"))
        .isEqualTo("http://localhost:4200");
    assertThat(configuration.getAllowCredentials()).isTrue();
  }

  @Test
  void corsRejectsDisallowedOrigin() {
    CorsConfiguration configuration =
        corsConfigurationFor("http://localhost:4300", HttpMethod.POST);

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkOrigin("http://localhost:4300")).isNull();
  }

  @Test
  void corsDoesNotUseWildcardWithCredentials() {
    CorsConfiguration configuration =
        corsConfigurationFor("http://localhost:4200", HttpMethod.POST);

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkOrigin("http://localhost:4200"))
        .isEqualTo("http://localhost:4200");
    assertThat(configuration.checkOrigin("http://localhost:4200")).isNotEqualTo("*");
    assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
    assertThat(configuration.getAllowCredentials()).isTrue();
  }

  @Test
  void preflightOptionsStillPermitted() {
    CorsConfiguration configuration = corsConfigurationFor("http://localhost:4200", HttpMethod.GET);

    assertThat(configuration).isNotNull();
    assertThat(configuration.checkOrigin("http://localhost:4200"))
        .isEqualTo("http://localhost:4200");
    assertThat(configuration.checkHttpMethod(HttpMethod.GET)).contains(HttpMethod.GET);
    assertThat(configuration.checkHttpMethod(HttpMethod.OPTIONS)).contains(HttpMethod.OPTIONS);
  }

  private CorsConfiguration corsConfigurationFor(String origin, HttpMethod requestedMethod) {
    CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource(corsProperties());
    var request =
        MockServerHttpRequest.options("/api/v1/secure-probe")
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, requestedMethod.name())
            .build();
    return source.getCorsConfiguration(MockServerWebExchange.from(request));
  }

  private CorsProperties corsProperties() {
    CorsProperties properties = new CorsProperties();
    properties.setAllowedOrigins(List.of("http://localhost:4200"));
    properties.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    properties.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token"));
    return properties;
  }
}
