package com.bct.ngtpa.apiservice.adapter.out.apim.retry;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimBusinessCallRetryIntegrationTest {

    @Test
    void safeBusinessCallRetriesTransient503WithinThreeTotalAttempts() {
        AtomicInteger calls = new AtomicInteger();
        ApimWebClientFacade facade = facade(calls, attempt -> attempt < 3
                ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("unavailable").build()
                : ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());

        StepVerifier.create(facade.post(
                        "/api/safe",
                        Map.of("x", "y"),
                        context("/api/safe"),
                        ApimRetryEligibility.SAFE_BUSINESS_CALL))
                .expectNext("{\"ok\":true}")
                .verifyComplete();

        assertThat(calls).hasValue(3);
    }

    @Test
    void safeBusinessCallStopsAfterConfiguredTotalAttempts() {
        AtomicInteger calls = new AtomicInteger();
        ApimWebClientFacade facade = facade(calls, ignored ->
                ClientResponse.create(HttpStatus.GATEWAY_TIMEOUT).body("timeout").build());

        StepVerifier.create(facade.post(
                        "/api/safe",
                        Map.of(),
                        context("/api/safe"),
                        ApimRetryEligibility.SAFE_BUSINESS_CALL))
                .expectError(ApimException.class)
                .verify();

        assertThat(calls).hasValue(3);
    }

    @Test
    void nonRetryableOperationPreservesSingleAttempt() {
        AtomicInteger calls = new AtomicInteger();
        ApimWebClientFacade facade = facade(calls, ignored ->
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("unavailable").build());

        StepVerifier.create(facade.post(
                        "/api/mutation",
                        Map.of(),
                        context("/api/mutation"),
                        ApimRetryEligibility.NON_RETRYABLE))
                .expectError(ApimException.class)
                .verify();

        assertThat(calls).hasValue(1);
    }

    @Test
    void http4xxIsNeverRetriedEvenForSafeOperation() {
        AtomicInteger calls = new AtomicInteger();
        ApimWebClientFacade facade = facade(calls, ignored ->
                ClientResponse.create(HttpStatus.BAD_REQUEST).body("business-error").build());

        StepVerifier.create(facade.post(
                        "/api/safe",
                        Map.of(),
                        context("/api/safe"),
                        ApimRetryEligibility.SAFE_BUSINESS_CALL))
                .expectError(ApimException.class)
                .verify();

        assertThat(calls).hasValue(1);
    }

    @Test
    void existingPostOverloadRemainsNonRetryableForBackwardCompatibility() {
        AtomicInteger calls = new AtomicInteger();
        ApimWebClientFacade facade = facade(calls, ignored ->
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("unavailable").build());

        StepVerifier.create(facade.post("/api/existing", Map.of(), context("/api/existing")))
                .expectError(ApimException.class)
                .verify();

        assertThat(calls).hasValue(1);
    }

    private ApimWebClientFacade facade(
            AtomicInteger calls,
            java.util.function.IntFunction<ClientResponse> response) {
        ExchangeFunction exchange = request -> Mono.just(response.apply(calls.incrementAndGet()));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        ApimProperties properties = new ApimProperties();
        properties.getRetry().getBusinessCall().setMaxAttempts(3);
        properties.getRetry().getBusinessCall().setBackoffMilliseconds(0);
        ApimCredentialProfile profile = new ApimCredentialProfile(
                "p1", "client", "secret", "key", null, "http://localhost", List.of(), null);
        ApimTokenService tokenService = new ApimTokenService(builder, properties) {
            @Override
            public Mono<String> getAccessToken(ApimCredentialProfile ignored) {
                return Mono.just("token");
            }
        };
        return new ApimWebClientFacade(
                WebClient.create(),
                builder,
                new ObjectMapper(),
                ignored -> Mono.just(profile),
                tokenService,
                new ApimCertificateService(builder, properties, null),
                properties);
    }

    private com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext context(
            String operation) {
        return new com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext(
                null, null, null, null, null, null, null, operation, Map.of());
    }
}
