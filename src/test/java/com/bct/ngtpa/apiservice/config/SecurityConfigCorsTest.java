package com.bct.ngtpa.apiservice.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SecurityConfigCorsTest.TestApplication.class,
    properties = {
      "cors.allowed-origins[0]=http://localhost:4200",
      "cors.allowed-methods[0]=GET",
      "cors.allowed-methods[1]=POST",
      "cors.allowed-methods[2]=PUT",
      "cors.allowed-methods[3]=PATCH",
      "cors.allowed-headers[0]=Account-Ref",
      "cors.allowed-headers[1]=Accept-Language",
      "cors.allowed-headers[2]=X-Request-Id",
      "cors.allowed-headers[3]=Content-Type",
      "cors.allowed-headers[4]=Authorization",
      "cors.allowed-headers[5]=X-Password-Validation-Token"
    })
@AutoConfigureWebTestClient
class SecurityConfigCorsTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private CorsProperties corsProperties;

  @Test
  void shippedDefaultCorsHeadersIncludePasswordValidationHeaders() throws Exception {
    var source =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .getFirst();
    var propertySources = new MutablePropertySources();
    propertySources.addFirst(source);
    var resolver = new PropertySourcesPropertyResolver(propertySources);
    String allowedHeaders =
        resolver.resolveRequiredPlaceholders(source.getProperty("cors.allowed-headers").toString());

    assertTrue(allowedHeaders.contains("X-Password-Validation-Token"));
    assertTrue(allowedHeaders.contains("Content-Type"));
    assertTrue(allowedHeaders.contains("Account-Ref"));
  }

  @ParameterizedTest
  @CsvSource({
    "PUT,/api/v1/personal-information,X-Password-Validation-Token",
    "POST,/api/v1/password-validation,Content-Type"
  })
  void allowsPasswordValidationHeadersOnRealPreflight(
      String method, String path, String requestedHeader) {
    assertTrue(corsProperties.getAllowedMethods().contains(method));
    assertTrue(corsProperties.getAllowedHeaders().contains(requestedHeader));
    webTestClient
        .options()
        .uri(path)
        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestedHeader)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
        .expectBody()
        .consumeWith(
            result ->
                assertTrue(
                    result
                        .getResponseHeaders()
                        .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                        .contains(requestedHeader)));
  }

  @Test
  void allowsConfiguredOriginForPreflightOnSecuredApiPath() {
    webTestClient
        .options()
        .uri("/api/v1/secure-probe")
        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
        .expectBody()
        .consumeWith(
            result -> {
              assertTrue(
                  result
                      .getResponseHeaders()
                      .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
                      .contains(HttpMethod.GET.name()));
              assertTrue(
                  result
                      .getResponseHeaders()
                      .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                      .contains("Authorization"));
              assertTrue(
                  result
                      .getResponseHeaders()
                      .getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                      .contains("Content-Type"));
            });
  }

  @Test
  void rejectsDisallowedOriginForPreflightOnSecuredApiPath() {
    webTestClient
        .options()
        .uri("/api/v1/secure-probe")
        .header(HttpHeaders.ORIGIN, "http://localhost:4300")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .consumeWith(
            result ->
                assertNull(
                    result.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)));
  }

  @RestController
  @RequestMapping(path = "/api/v1/secure-probe", produces = MediaType.TEXT_PLAIN_VALUE)
  static class SecuredProbeController {

    @GetMapping
    Mono<String> get() {
      return Mono.just("secured");
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration"
      })
  @EnableConfigurationProperties(CorsProperties.class)
  @Import({SecurityConfig.class, SecuredProbeController.class})
  static class TestApplication {}
}
