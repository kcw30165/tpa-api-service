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
import org.springframework.http.HttpMethod;
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
 * Verifies that all business API endpoints under /api/v1 require authentication.
 * No endpoint that returns business data should be accessible without credentials.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = SecurityConfigAuthTest.TestApplication.class,
    properties = "cors.allowed-origins[0]=http://localhost:4200")
@AutoConfigureWebTestClient
class SecurityConfigAuthTest {

    private static final String REFRESH_PATH = "/api/v1/internal/reference-date/refresh";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void unauthenticatedGetNotificationsIsRejected() {
        webTestClient.get()
                .uri("/api/v1/notifications")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedPatchNotificationsIsRejected() {
        webTestClient.patch()
                .uri("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedGetContributionsIsRejected() {
        webTestClient.get()
                .uri("/api/v1/contributions")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedGetContributionsExportIsRejected() {
        webTestClient.get()
                .uri("/api/v1/contributions/export")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedInternalReferenceDateRefreshIsRejected() {
        webTestClient.post()
                .uri(REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void corsPreflightOptionsIsPermittedWithoutAuthentication() {
        // OPTIONS preflight must pass without credentials so browsers can negotiate CORS.
        webTestClient.options()
                .uri("/api/v1/notifications")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", HttpMethod.GET.name())
                .exchange()
                // Security permits OPTIONS; the actual CORS allow/reject decision is handled
                // by the CORS configuration source (allowed-origins list).
                .expectStatus().isOk();
    }

    // Stub controllers for the business endpoint paths used in tests above.
    // Spring Security rejects unauthenticated requests before they reach the handler,
    // so these stubs exist only to satisfy handler mapping during context startup.

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

        @GetMapping("/export")
        Mono<String> export() { return Mono.just(""); }
    }

    @RestController
    @RequestMapping(path = "/api/v1/internal/reference-date", produces = MediaType.APPLICATION_JSON_VALUE)
    static class StubInternalReferenceDateController {
        @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
        Mono<String> refresh(@RequestBody String body) { return Mono.just("{}"); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration"
    })
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
