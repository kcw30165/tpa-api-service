package com.bct.ngtpa.apiservice.adapter.out.apim.oauth;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApimTokenServiceTest {

    @Test
    void fetchesAndCachesToken() {
        AtomicInteger counter = new AtomicInteger();
        String tokenJson = "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            ClientResponse resp = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(tokenJson)
                    .build();
            return Mono.just(resp);
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimTokenService svc = new ApimTokenService(builder, props);

        ApimCredentialProfile profile = new ApimCredentialProfile("default","cid","secret","akey",null,null, List.of(),null);

        String t1 = svc.getAccessToken(profile).block();
        String t2 = svc.getAccessToken(profile).block();

        assertEquals("tok-1", t1);
        assertEquals("tok-1", t2);
        assertEquals(1, counter.get());
    }

    @Test
    void concurrentRefreshSharesInFlight() {
        AtomicInteger counter = new AtomicInteger();
        String tokenJson = "{\"access_token\":\"tok-2\",\"token_type\":\"Bearer\",\"expires_in\":3600}";

        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            ClientResponse resp = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(tokenJson)
                    .build();
            return Mono.just(resp);
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimTokenService svc = new ApimTokenService(builder, props);

        ApimCredentialProfile profile = new ApimCredentialProfile("default","cid","secret","akey",null,null, List.of(),null);

        Mono<?> m1 = svc.refreshAccessToken(profile);
        Mono<?> m2 = svc.refreshAccessToken(profile);

        Mono.zip(m1, m2).block();

        assertEquals(1, counter.get());
    }

    @Test
    void usesProfileTokenUriWhenNotNull() {
        String tokenJson = "{\"access_token\":\"tok-custom\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
        AtomicInteger counter = new AtomicInteger();

        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            ClientResponse resp = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(tokenJson)
                    .build();
            return Mono.just(resp);
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimTokenService svc = new ApimTokenService(builder, props);

        // Profile with explicit tokenUri — verifies profile.tokenUri() != null branch is taken
        ApimCredentialProfile profile = new ApimCredentialProfile("p1", "cid", "secret", "akey",
                "https://custom-token-uri.example.com", null, List.of(), null);

        String token = svc.getAccessToken(profile).block();

        assertEquals("tok-custom", token);
        assertEquals(1, counter.get());
    }

    @Test
    void expiredCacheEntryTriggersFreshFetch() {
        AtomicInteger counter = new AtomicInteger();
        String tokenJson = "{\"access_token\":\"tok-exp\",\"token_type\":\"Bearer\",\"expires_in\":0}";

        ExchangeFunction ef = req -> {
            counter.incrementAndGet();
            ClientResponse resp = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(tokenJson)
                    .build();
            return Mono.just(resp);
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        ApimProperties props = new ApimProperties();
        ApimTokenService svc = new ApimTokenService(builder, props);

        ApimCredentialProfile profile = new ApimCredentialProfile("p2", "cid", "secret", "akey", null, null,
                List.of(), null);

        svc.getAccessToken(profile).block();
        svc.getAccessToken(profile).block();

        // expires_in=0 means token is always expired, so every call fetches fresh
        assertEquals(2, counter.get());
    }
}
