package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Verifies the K8s / external-auth-layer deployment scenario.
 *
 * <p>When {@code api.security.require-authentication=false} (set via env var
 * {@code API_SECURITY_REQUIRE_AUTHENTICATION=false} in a K8s ConfigMap or Deployment),
 * in-process auth is disabled and all traffic is trusted at the network boundary.
 * The actual auth enforcement is expected to be handled by an ingress controller or API gateway.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SecurityConfigAuthDisabledTest.TestApplication.class,
    properties = {
        "cors.allowed-origins[0]=http://localhost:4200",
        "api.security.require-authentication=false"
    })
@AutoConfigureWebTestClient
class SecurityConfigAuthDisabledTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void unauthenticatedGetNotificationsIsAllowedWhenAuthDisabled() {
        webTestClient.get().uri("/api/v1/notifications").exchange().expectStatus().isOk();
    }

    @Test
    void unauthenticatedPatchNotificationsIsAllowedWhenAuthDisabled() {
        webTestClient.patch()
                .uri("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void unauthenticatedGetContributionsIsAllowedWhenAuthDisabled() {
        webTestClient.get().uri("/api/v1/contributions").exchange().expectStatus().isOk();
    }

    @Test
    void unauthenticatedInternalReferenceDateRefreshIsAllowedWhenAuthDisabled() {
        webTestClient.post()
                .uri("/internal/reference-date/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }

    @RestController
    @RequestMapping(path = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    static class StubNotificationsController {
        @GetMapping
        Mono<String> get() { return Mono.just("[]"); }

        @PatchMapping
        Mono<String> patch() { return Mono.just("{}"); }
    }

    @RestController
    @RequestMapping(path = "/api/v1/contributions", produces = MediaType.APPLICATION_JSON_VALUE)
    static class StubContributionsController {
        @GetMapping
        Mono<String> get() { return Mono.just("{}"); }
    }

    @RestController
    @RequestMapping(path = "/internal/reference-date", produces = MediaType.APPLICATION_JSON_VALUE)
    static class StubInternalReferenceDateController {
        @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
        Mono<String> refresh(@RequestBody String body) { return Mono.just("{}"); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(CorsProperties.class)
    @Import({
        SecurityConfig.class,
        StubNotificationsController.class,
        StubContributionsController.class,
        StubInternalReferenceDateController.class
    })
    static class TestApplication {
    }
}
