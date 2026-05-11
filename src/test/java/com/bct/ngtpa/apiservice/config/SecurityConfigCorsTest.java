package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
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
    properties = "cors.allowed-origins[0]=http://localhost:4200")
@AutoConfigureWebTestClient
class SecurityConfigCorsTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void allowsConfiguredOriginForPreflightOnSecuredApiPath() {
        webTestClient.options()
                .uri("/api/v1/secure-probe")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
                .expectBody()
                .consumeWith(result -> {
                    assertTrue(result.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
                        .contains(HttpMethod.GET.name()));
                    assertTrue(result.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                        .contains("Authorization"));
                    assertTrue(result.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                        .contains("Content-Type"));
                });
    }

    @Test
    void rejectsDisallowedOriginForPreflightOnSecuredApiPath() {
        webTestClient.options()
                .uri("/api/v1/secure-probe")
                .header(HttpHeaders.ORIGIN, "http://localhost:4300")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .consumeWith(result -> assertNull(
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
    @EnableAutoConfiguration
    @EnableConfigurationProperties(CorsProperties.class)
    @Import({ SecurityConfig.class, SecuredProbeController.class })
    static class TestApplication {
    }
}