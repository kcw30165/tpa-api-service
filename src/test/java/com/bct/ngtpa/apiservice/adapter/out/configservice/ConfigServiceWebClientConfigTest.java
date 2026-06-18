package com.bct.ngtpa.apiservice.adapter.out.configservice;

import com.bct.ngtpa.apiservice.adapter.out.configservice.client.ConfigServiceWebClientConfig;
import com.bct.ngtpa.apiservice.adapter.out.configservice.config.ConfigServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigServiceWebClientConfig}.
 *
 * <p>Verifies that the produced WebClient includes an {@code Authorization: Basic ...}
 * header when credentials are configured, and omits it when credentials are absent.
 */
class ConfigServiceWebClientConfigTest {

    private ConfigServiceProperties propsWithCredentials(String username, String password) {
        var props = new ConfigServiceProperties();
        props.setBaseUrl("http://config-service");
        props.setUsername(username);
        props.setPassword(password);
        return props;
    }

    private ConfigServiceProperties propsWithoutCredentials() {
        var props = new ConfigServiceProperties();
        props.setBaseUrl("http://config-service");
        return props;
    }

    private static ExchangeFunction capturingAuth(AtomicReference<String> captured) {
        return req -> {
            captured.set(req.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("[]")
                    .build());
        };
    }

    @Test
    void sendsBasicAuthHeaderWhenCredentialsConfigured() {
        var props = propsWithCredentials("svc-user", "svc-pass");
        AtomicReference<String> capturedAuth = new AtomicReference<>();

        var webClient = new ConfigServiceWebClientConfig()
                .configServiceWebClient(props)
                .mutate()
                .exchangeFunction(capturingAuth(capturedAuth))
                .build();

        webClient.get().uri("/api/configs").retrieve().toBodilessEntity().block();

        assertNotNull(capturedAuth.get(), "Authorization header must be present when credentials are configured");
        assertTrue(capturedAuth.get().startsWith("Basic "), "Authorization must use Basic scheme");
        String decoded = new String(Base64.getDecoder().decode(capturedAuth.get().substring("Basic ".length())));
        assertEquals("svc-user:svc-pass", decoded, "Basic credentials must match configured username:password");
    }

    @Test
    void omitsAuthHeaderWhenCredentialsNotConfigured() {
        var props = propsWithoutCredentials();
        AtomicReference<String> capturedAuth = new AtomicReference<>();

        var webClient = new ConfigServiceWebClientConfig()
                .configServiceWebClient(props)
                .mutate()
                .exchangeFunction(capturingAuth(capturedAuth))
                .build();

        webClient.get().uri("/api/configs").retrieve().toBodilessEntity().block();

        assertNull(capturedAuth.get(), "Authorization header must not be present when no credentials configured");
    }
}
