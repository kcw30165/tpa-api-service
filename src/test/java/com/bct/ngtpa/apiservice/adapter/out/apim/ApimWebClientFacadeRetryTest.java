package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenCacheEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApimWebClientFacadeRetryTest {

    @Test
    void invalidTokenEvictsAndRetriesOnce() {
        AtomicInteger serverCalls = new AtomicInteger();

        ExchangeFunction ef = req -> {
            serverCalls.incrementAndGet();
            String auth = req.headers().getFirst("Authorization");
            if ("Bearer old-token".equals(auth)) {
                String err = "{\"id\":\"1\",\"error\":\"invalid_token\",\"error_description\":\"expired\"}";
                return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).body(err).build());
            }
            if ("Bearer new-token".equals(auth)) {
                String ok = "{\"ok\":true}";
                return Mono.just(ClientResponse.create(HttpStatus.OK).body(ok).build());
            }
            String unknown = "{\"id\":\"x\",\"error\":\"unknown\"}";
            return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).body(unknown).build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();

        // Token service stub that starts with old-token and refreshes to new-token
        AtomicReference<String> tokenRef = new AtomicReference<>("old-token");
        AtomicInteger refreshCount = new AtomicInteger();

        ApimTokenService tokenService = new ApimTokenService(builder, props) {
            @Override
            public Mono<String> getAccessToken(ApimCredentialProfile profile) {
                return Mono.just(tokenRef.get());
            }

            @Override
            public Mono<ApimTokenCacheEntry> refreshAccessToken(ApimCredentialProfile profile) {
                refreshCount.incrementAndGet();
                tokenRef.set("new-token");
                ApimTokenCacheEntry e = new ApimTokenCacheEntry(profile.profileId(), "new-token", "Bearer", System.currentTimeMillis() + 3600_000L, null);
                return Mono.just(e);
            }
        };

        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
        ApimCredentialProfileResolver resolver = ctx -> Mono.just(profile);

        ApimCertificateService certificateService = new ApimCertificateService(builder, props, null);

        ApimWebClientFacade facade = new ApimWebClientFacade(WebClient.create(), builder, new ObjectMapper(), resolver, tokenService, certificateService, props);

        String resp = facade.post("/api/test", Map.of("x", "y")).block();

        assertEquals("{\"ok\":true}", resp);
        assertEquals(2, serverCalls.get(), "Server should be called twice: first failing token, then retry");
        assertEquals(1, refreshCount.get(), "Token refresh should be invoked exactly once");
    }

    @Test
    void encryptedRequestsIncludeCertificateHeader() {
        AtomicReference<String> certificateHeader = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            certificateHeader.set(req.headers().getFirst("Certificate"));
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        props.getEncryption().setEnabled(true);

        ApimTokenService tokenService = new ApimTokenService(builder, props) {
            @Override
            public Mono<String> getAccessToken(ApimCredentialProfile profile) {
                return Mono.just("tok");
            }
        };

        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
        ApimCredentialProfileResolver resolver = ctx -> Mono.just(profile);
        ApimCertificateService certificateService = new ApimCertificateService(builder, props, null);

        ApimWebClientFacade facade = new ApimWebClientFacade(WebClient.create(), builder, new ObjectMapper(), resolver, tokenService, certificateService, props) {
            @Override
            protected String getCertificateHeaderValue() {
                return "app-cert-header";
            }
        };

        facade.post("/api/test", Map.of("x", "y")).block();

        assertEquals("app-cert-header", certificateHeader.get());
    }

    @Test
    void malformedInvalidTokenBodyStillRefreshesAndRetries() {
        AtomicInteger serverCalls = new AtomicInteger();

        ExchangeFunction ef = req -> {
            serverCalls.incrementAndGet();
            String auth = req.headers().getFirst("Authorization");
            if ("Bearer old-token".equals(auth)) {
                String err = "{\n  \"id\" : \"1\",\n \"error\":\"invalid_token\"\n \"error_desciption\":\"The access token is expired or invalid\"\n}";
                return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).body(err).build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();

        AtomicReference<String> tokenRef = new AtomicReference<>("old-token");
        AtomicInteger refreshCount = new AtomicInteger();

        ApimTokenService tokenService = new ApimTokenService(builder, props) {
            @Override
            public Mono<String> getAccessToken(ApimCredentialProfile profile) {
                return Mono.just(tokenRef.get());
            }

            @Override
            public Mono<ApimTokenCacheEntry> refreshAccessToken(ApimCredentialProfile profile) {
                refreshCount.incrementAndGet();
                tokenRef.set("new-token");
                return Mono.just(new ApimTokenCacheEntry(profile.profileId(), "new-token", "Bearer",
                        System.currentTimeMillis() + 3600_000L, null));
            }
        };

        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
        ApimCredentialProfileResolver resolver = ctx -> Mono.just(profile);
        ApimCertificateService certificateService = new ApimCertificateService(builder, props, null);

        ApimWebClientFacade facade = new ApimWebClientFacade(WebClient.create(), builder, new ObjectMapper(), resolver, tokenService, certificateService, props);

        String resp = facade.post("/api/test", Map.of("x", "y")).block();

        assertEquals("{\"ok\":true}", resp);
        assertEquals(2, serverCalls.get());
        assertEquals(1, refreshCount.get());
    }

    @Test
    void invalidPublicKeyEvictsCertificateAndRetriesOnce() {
        AtomicInteger serverCalls = new AtomicInteger();
        AtomicInteger evictions = new AtomicInteger();

        ExchangeFunction ef = req -> {
            if (serverCalls.getAndIncrement() == 0) {
                return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid_public_key\"}")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimTokenService tokenService = fixedTokenService(builder, props, "tok");
        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
        ApimCredentialProfileResolver resolver = ctx -> Mono.just(profile);
        ApimCertificateService certificateService = trackingCertificateService(builder, props, evictions);

        ApimWebClientFacade facade = new ApimWebClientFacade(
                WebClient.create(), builder, new ObjectMapper(), resolver, tokenService, certificateService, props);

        assertEquals("{\"ok\":true}", facade.post("/api/test", Map.of("x", "y")).block());
        assertEquals(2, serverCalls.get());
        assertEquals(1, evictions.get());
    }

    @Test
    void invalidPublicKeyRetriesOnlyOnceThenPropagatesError() {
        AtomicInteger serverCalls = new AtomicInteger();
        AtomicInteger evictions = new AtomicInteger();

        ExchangeFunction ef = req -> {
            serverCalls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"invalid_public_key\"}")
                    .build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);

        ApimWebClientFacade facade = new ApimWebClientFacade(
                WebClient.create(),
                builder,
                new ObjectMapper(),
                ctx -> Mono.just(profile),
                fixedTokenService(builder, props, "tok"),
                trackingCertificateService(builder, props, evictions),
                props);

        StepVerifier.create(facade.post("/api/test", Map.of("x", "y")))
                .expectErrorSatisfies(ex -> {
                    assertEquals(ApimException.class, ex.getClass());
                    assertEquals(
                            "APIM request failed with status=400 body={\"error\":\"invalid_public_key\"}",
                            ex.getMessage());
                })
                .verify();

        assertEquals(2, serverCalls.get());
        assertEquals(1, evictions.get());
    }

    @Test
    void nonRetryableErrorsPropagateImmediately() {
        AtomicInteger serverCalls = new AtomicInteger();
        AtomicInteger refreshCount = new AtomicInteger();

        ExchangeFunction ef = req -> {
            serverCalls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).body("not-json").build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimTokenService tokenService = new ApimTokenService(builder, props) {
            @Override
            public Mono<String> getAccessToken(ApimCredentialProfile profile) {
                return Mono.just("tok");
            }

            @Override
            public Mono<ApimTokenCacheEntry> refreshAccessToken(ApimCredentialProfile profile) {
                refreshCount.incrementAndGet();
                return Mono.error(new IllegalStateException("should not refresh"));
            }
        };

        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
        ApimWebClientFacade facade = new ApimWebClientFacade(
                WebClient.create(),
                builder,
                new ObjectMapper(),
                ctx -> Mono.just(profile),
                tokenService,
                trackingCertificateService(builder, props, new AtomicInteger()),
                props);

        StepVerifier.create(facade.post("/api/test", Map.of("x", "y")))
                .expectErrorSatisfies(ex -> {
                    assertEquals(ApimException.class, ex.getClass());
                    assertEquals("APIM request failed with status=502 body=not-json", ex.getMessage());
                })
                .verify();

        assertEquals(1, serverCalls.get());
        assertEquals(0, refreshCount.get());
    }

    @Test
    void usesConfiguredBaseUrlAndSkipsBlankCertificateHeader() {
        AtomicReference<URI> requestUrl = new AtomicReference<>();
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        AtomicReference<String> certificateHeader = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            requestUrl.set(req.url());
            apiKeyHeader.set(req.headers().getFirst("X-Api-Key"));
            certificateHeader.set(req.headers().getFirst("Certificate"));
            return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        props.setBaseUrl("http://configured.example");
        props.setApiKeyHeaderName("X-Api-Key");
        props.getEncryption().setEnabled(true);

        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", null, null, null, List.of(), null);
        ApimWebClientFacade facade = new ApimWebClientFacade(
                WebClient.create(),
                builder,
                new ObjectMapper(),
                captureResolver(profile),
                fixedTokenService(builder, props, "tok"),
                trackingCertificateService(builder, props, new AtomicInteger()),
                props) {
            @Override
            protected String getCertificateHeaderValue() {
                return "  ";
            }
        };

        assertEquals("{\"ok\":true}", facade.post("/api/test", Map.of("x", "y")).block());
        assertEquals("http://configured.example/api/test", requestUrl.get().toString());
        assertEquals("", apiKeyHeader.get());
        assertEquals(null, certificateHeader.get());
    }

    private static ApimTokenService fixedTokenService(WebClient.Builder builder, ApimProperties props, String token) {
        return new ApimTokenService(builder, props) {
            @Override
            public Mono<String> getAccessToken(ApimCredentialProfile profile) {
                return Mono.just(token);
            }
        };
    }

    private static ApimCertificateService trackingCertificateService(
            WebClient.Builder builder, ApimProperties props, AtomicInteger evictions) {
        return new ApimCertificateService(builder, props, null) {
            @Override
            public void evictCertificate(String profileId) {
                evictions.incrementAndGet();
            }
        };
    }

    private static ApimCredentialProfileResolver captureResolver(ApimCredentialProfile profile) {
        return new ApimCredentialProfileResolver() {
            @Override
            public Mono<ApimCredentialProfile> resolve(ApimCredentialResolutionContext context) {
                assertEquals("/api/test", context.operationName());
                return Mono.just(profile);
            }
        };
    }
}
