package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenCacheEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
}
