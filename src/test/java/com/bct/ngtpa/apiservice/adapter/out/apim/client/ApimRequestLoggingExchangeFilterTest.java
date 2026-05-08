package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.config.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.config.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.config.logging.RequestLoggingWebFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

class ApimRequestLoggingExchangeFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSanitizer loggingSanitizer = loggingSanitizerWithDefaults();
    private final ApimRequestLoggingExchangeFilter filter =
            new ApimRequestLoggingExchangeFilter(loggingSanitizer, objectMapper);

    private static LoggingSanitizer loggingSanitizerWithDefaults() {
        LoggingSanitizerProperties props = new LoggingSanitizerProperties();
        props.setSensitiveTokens(java.util.List.of(
                "authorization", "token", "secret", "password",
                "apiKey", "Certificate", "clientSecret"
        ));
        return new LoggingSanitizer(new ObjectMapper(), props);
    }

    private ListAppender<ILoggingEvent> listAppender;
    private Logger filterLogger;

    @BeforeEach
    void setUpLogCapture() {
        filterLogger = (Logger) LoggerFactory.getLogger(ApimRequestLoggingExchangeFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        filterLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        filterLogger.detachAppender(listAppender);
    }

    @Test
    void logEventIsValidJson() throws Exception {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.POST, URI.create("https://api.example.test/notifications"))
                .header("Accept", "application/json")
                .build();

        invokeFilter(exchangeFilter, request, "req-001");

        assertNotNull(listAppender.list);
        assertEquals(1, listAppender.list.size());
        String message = listAppender.list.get(0).getFormattedMessage();
        objectMapper.readTree(message); // valid JSON - no exception thrown
    }

    @Test
    void logEventNameIsApimRequest() throws Exception {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .build();

        invokeFilter(exchangeFilter, request, "req-002");

        String message = listAppender.list.get(0).getFormattedMessage();
        var node = objectMapper.readTree(message);
        assertEquals("apim.request", node.get("event").asText());
    }

    @Test
    void logEventContainsMethodUrlAndRequestId() throws Exception {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.POST, URI.create("https://api.example.test/contributions"))
                .build();

        invokeFilter(exchangeFilter, request, "req-xyz");

        String message = listAppender.list.get(0).getFormattedMessage();
        var node = objectMapper.readTree(message);
        assertEquals("POST", node.get("method").asText());
        assertEquals("https://api.example.test/contributions", node.get("url").asText());
        assertEquals("req-xyz", node.get("requestId").asText());
        assertNotNull(node.get("headers"));
    }

    @Test
    void sensitiveHeadersAreMaskedInLogOutput() throws Exception {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .header("Authorization", "Bearer super-secret-token")
                .header("Certificate", "encoded-cert-data")
                .header("clientSecret", "my-secret-value")
                .build();

        invokeFilter(exchangeFilter, request, "req-003");

        String message = listAppender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("apim.request"), "event name should be present");
        assertTrue(!message.contains("super-secret-token"), "Authorization value must be masked");
        assertTrue(!message.contains("encoded-cert-data"), "Certificate value must be masked");
        assertTrue(!message.contains("my-secret-value"), "clientSecret value must be masked");
    }

    @Test
    void requestIsPassedThroughUnmodified() {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .header("Accept", "application/json")
                .build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }).block();

        assertNotNull(captured.get());
        assertEquals(request.url(), captured.get().url());
        assertEquals(request.method(), captured.get().method());
    }

    private void invokeFilter(ExchangeFilterFunction exchangeFilter, ClientRequest request, String requestId) {
        exchangeFilter.filter(request, req -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .contextWrite(ctx -> ctx.put(RequestLoggingWebFilter.REQUEST_ID_CONTEXT_KEY, requestId))
                .block();
    }
}
