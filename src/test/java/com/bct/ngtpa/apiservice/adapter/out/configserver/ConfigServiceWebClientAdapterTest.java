package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.adapter.out.configserver.config.ConfigServiceProperties;
import com.bct.ngtpa.apiservice.application.dto.ConfigEntry;
import com.bct.ngtpa.apiservice.application.dto.ConfigQuery;
import com.bct.ngtpa.apiservice.application.dto.ConfigUpsertCommand;
import com.bct.ngtpa.apiservice.exception.ConfigServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adapter unit tests for Config Service GET /api/configs.
 *
 * <p>Assumed ConfigServiceResponse JSON field names: application, profile, label,
 * configKey, configValue (camelCase). Must be verified against the actual Config
 * Service implementation before going to production.
 */
class ConfigServiceWebClientAdapterTest {

    private static final String JSON_TWO_ENTRIES = """
            [
              {
                "application": "myapp",
                "profile": "default",
                "label": "master",
                "configKey": "my.key",
                "configValue": "hello"
              },
              {
                "application": "myapp",
                "profile": "default",
                "label": "master",
                "configKey": "other.key",
                "configValue": "world"
              }
            ]
            """;

    private static final String JSON_ONE_ENTRY = """
            {
              "application": "myapp",
              "profile": "default",
              "label": "master",
              "configKey": "my.key",
              "configValue": "new-value"
            }
            """;

    private ConfigServiceWebClientAdapter adapterWith(ExchangeFunction ef) {
        ConfigServiceProperties props = new ConfigServiceProperties();
        props.setBaseUrl("http://localhost:8099");
        props.setTimeoutMilliseconds(5000);
        WebClient webClient = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .exchangeFunction(ef)
                .build();
        return new ConfigServiceWebClientAdapter(webClient, props);
    }

    private static ClientResponse okJson(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build();
    }

    // ── GET path and method ───────────────────────────────────────────────────

    @Test
    void listConfigs_sendsGetToConfigsPath() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            capturedUri.set(req.url());
            capturedMethod.set(req.method());
            return okMono("[]");
        };

        adapterWith(ef).listConfigs(new ConfigQuery(null, null, null, null)).block();

        assertEquals(HttpMethod.GET, capturedMethod.get());
        assertEquals("/api/configs", capturedUri.get().getPath());
    }

    // ── Query parameter filtering ─────────────────────────────────────────────

    @Test
    void listConfigs_includesAllQueryParamsWhenPresent() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            capturedUri.set(req.url());
            return okMono("[]");
        };

        adapterWith(ef).listConfigs(new ConfigQuery("myapp", "staging", "main", "my.key")).block();

        String query = capturedUri.get().getQuery();
        assertTrue(query.contains("application=myapp"), "application param expected");
        assertTrue(query.contains("profile=staging"), "profile param expected");
        assertTrue(query.contains("label=main"), "label param expected");
        assertTrue(query.contains("configKey=my.key"), "configKey param expected");
    }

    @Test
    void listConfigs_omitsNullAndBlankQueryParams() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            capturedUri.set(req.url());
            return okMono("[]");
        };

        // application present, profile null, label blank, configKey present
        adapterWith(ef).listConfigs(new ConfigQuery("myapp", null, "", "my.key")).block();

        String rawQuery = capturedUri.get().getRawQuery();
        assertTrue(rawQuery == null || !rawQuery.contains("profile"), "null profile must be omitted");
        assertTrue(rawQuery == null || !rawQuery.contains("label"), "blank label must be omitted");
        assertTrue(capturedUri.get().getQuery().contains("application=myapp"));
        assertTrue(capturedUri.get().getQuery().contains("configKey=my.key"));
    }

    @Test
    void listConfigs_sendsNoQueryParamsWhenAllAreBlank() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            capturedUri.set(req.url());
            return okMono("[]");
        };

        adapterWith(ef).listConfigs(new ConfigQuery(null, null, null, null)).block();

        assertNull(capturedUri.get().getQuery(), "query string must be absent when all params are blank");
    }

    // ── Response mapping ──────────────────────────────────────────────────────

    @Test
    void listConfigs_mapsJsonResponseToConfigEntryList() {
        ExchangeFunction ef = req -> okMono(JSON_TWO_ENTRIES);

        List<ConfigEntry> result = adapterWith(ef)
                .listConfigs(new ConfigQuery("myapp", "default", "master", null))
                .block();

        assertNotNull(result);
        assertEquals(2, result.size());

        ConfigEntry first = result.get(0);
        assertEquals("myapp", first.application());
        assertEquals("default", first.profile());
        assertEquals("master", first.label());
        assertEquals("my.key", first.configKey());
        assertEquals("hello", first.configValue());

        ConfigEntry second = result.get(1);
        assertEquals("other.key", second.configKey());
        assertEquals("world", second.configValue());
    }

    @Test
    void listConfigs_emptyResponseMapsToEmptyList() {
        ExchangeFunction ef = req -> okMono("[]");

        List<ConfigEntry> result = adapterWith(ef)
                .listConfigs(new ConfigQuery(null, null, null, null))
                .block();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    @Test
    void listConfigs_propagatesConfigServiceExceptionOn4xx() {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.NOT_FOUND).body("").build());

        StepVerifier.create(adapterWith(ef).listConfigs(new ConfigQuery(null, null, null, null)))
                .expectError(ConfigServiceException.class)
                .verify();
    }

    @Test
    void listConfigs_propagatesConfigServiceExceptionOn5xx() {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("").build());

        StepVerifier.create(adapterWith(ef).listConfigs(new ConfigQuery(null, null, null, null)))
                .expectError(ConfigServiceException.class)
                .verify();
    }

    @Test
    void listConfigs_configServiceExceptionCarriesStatusCode() {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).body("").build());

        StepVerifier.create(adapterWith(ef).listConfigs(new ConfigQuery(null, null, null, null)))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(ConfigServiceException.class, ex);
                    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                            ((ConfigServiceException) ex).getStatusCode());
                })
                .verify();
    }

    // ── PUT path and method ───────────────────────────────────────────────────

    @Test
    void upsertConfig_sendsPutToConfigsPath() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            capturedUri.set(req.url());
            capturedMethod.set(req.method());
            return okMono(JSON_ONE_ENTRY);
        };

        ConfigUpsertCommand command = new ConfigUpsertCommand("myapp", "default", "master", "my.key", "new-value");
        adapterWith(ef).upsertConfig(command).block();

        assertEquals(HttpMethod.PUT, capturedMethod.get());
        assertEquals("/api/configs", capturedUri.get().getPath());
    }

    // ── PUT request body serialization ────────────────────────────────────────

    @Test
    void upsertConfig_serializesCommandToRequestBody() {
        AtomicReference<String> capturedBody = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            MockClientHttpRequest mockReq = new MockClientHttpRequest(req.method(), req.url());
            req.writeTo(mockReq, ExchangeStrategies.withDefaults()).block();
            capturedBody.set(mockReq.getBodyAsString().block());
            return okMono(JSON_ONE_ENTRY);
        };

        ConfigUpsertCommand command = new ConfigUpsertCommand("myapp", "default", "master", "my.key", "new-value");
        adapterWith(ef).upsertConfig(command).block();

        String body = capturedBody.get();
        assertNotNull(body, "request body must not be null");
        assertTrue(body.contains("\"myapp\""), "application expected in body");
        assertTrue(body.contains("\"default\""), "profile expected in body");
        assertTrue(body.contains("\"master\""), "label expected in body");
        assertTrue(body.contains("\"my.key\""), "configKey expected in body");
        assertTrue(body.contains("\"new-value\""), "configValue expected in body");
    }

    // ── PUT response mapping ──────────────────────────────────────────────────

    @Test
    void upsertConfig_mapsJsonResponseToConfigEntry() {
        ExchangeFunction ef = req -> okMono(JSON_ONE_ENTRY);

        ConfigUpsertCommand command = new ConfigUpsertCommand("myapp", "default", "master", "my.key", "new-value");
        ConfigEntry result = adapterWith(ef).upsertConfig(command).block();

        assertNotNull(result);
        assertEquals("myapp", result.application());
        assertEquals("default", result.profile());
        assertEquals("master", result.label());
        assertEquals("my.key", result.configKey());
        assertEquals("new-value", result.configValue());
    }

    // ── PUT error mapping ─────────────────────────────────────────────────────

    @Test
    void upsertConfig_propagatesConfigServiceExceptionOn4xx() {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.BAD_REQUEST).body("").build());

        ConfigUpsertCommand command = new ConfigUpsertCommand("myapp", "default", "master", "my.key", "new-value");
        StepVerifier.create(adapterWith(ef).upsertConfig(command))
                .expectError(ConfigServiceException.class)
                .verify();
    }

    @Test
    void upsertConfig_propagatesConfigServiceExceptionOn5xx() {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("").build());

        ConfigUpsertCommand command = new ConfigUpsertCommand("myapp", "default", "master", "my.key", "new-value");
        StepVerifier.create(adapterWith(ef).upsertConfig(command))
                .expectError(ConfigServiceException.class)
                .verify();
    }

    // ── DELETE path and method ───────────────────────────────────────────────

    @Test
    void deleteConfig_sendsDeleteToCorrectPath() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        AtomicReference<HttpMethod> capturedMethod = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            capturedUri.set(req.url());
            capturedMethod.set(req.method());
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        };

        adapterWith(ef).deleteConfig("myapp", "default", "master", "my.key").block();

        assertEquals(HttpMethod.DELETE, capturedMethod.get());
        assertEquals("/api/configs/myapp/default/master/my.key", capturedUri.get().getPath());
    }

    @Test
    void deleteConfig_encodesSpecialCharactersInPathVariables() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();

        ExchangeFunction ef = req -> {
            capturedUri.set(req.url());
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        };

        adapterWith(ef).deleteConfig("my app", "default", "master", "my.key").block();

        String rawPath = capturedUri.get().getRawPath();
        assertTrue(rawPath.contains("my%20app"), "space in application must be percent-encoded; raw path: " + rawPath);
    }

    // ── DELETE 204 success ────────────────────────────────────────────────────

    @Test
    void deleteConfig_completesSuccessfullyOn204() {
        ExchangeFunction ef = req ->
                Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());

        StepVerifier.create(adapterWith(ef).deleteConfig("myapp", "default", "master", "my.key"))
                .verifyComplete();
    }

    // ── DELETE missing required fields ────────────────────────────────────────

    @Test
    void deleteConfig_rejectsNullApplicationWithoutSendingRequest() {
        AtomicBoolean requestSent = new AtomicBoolean(false);
        ExchangeFunction ef = req -> {
            requestSent.set(true);
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        };

        StepVerifier.create(adapterWith(ef).deleteConfig(null, "default", "master", "my.key"))
                .expectError(IllegalArgumentException.class)
                .verify();

        assertFalse(requestSent.get(), "no HTTP request must be sent when application is null");
    }

    // ── DELETE error mapping ──────────────────────────────────────────────────

    @Test
    void deleteConfig_propagatesConfigServiceExceptionOn4xx() {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.NOT_FOUND).body("").build());

        StepVerifier.create(adapterWith(ef).deleteConfig("myapp", "default", "master", "my.key"))
                .expectError(ConfigServiceException.class)
                .verify();
    }

    @Test
    void deleteConfig_propagatesConfigServiceExceptionOn5xx() {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("").build());

        StepVerifier.create(adapterWith(ef).deleteConfig("myapp", "default", "master", "my.key"))
                .expectError(ConfigServiceException.class)
                .verify();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Mono<ClientResponse> okMono(String body) {
        return Mono.just(okJson(body));
    }
}
