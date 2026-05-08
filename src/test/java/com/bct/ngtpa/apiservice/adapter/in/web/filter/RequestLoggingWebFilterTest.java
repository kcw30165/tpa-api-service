package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequestLoggingWebFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;
    private RequestLoggingWebFilter filter;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingWebFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        filter = new RequestLoggingWebFilter(enabledProperties(), sanitizer(), OBJECT_MAPPER);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    // ── Request ID resolution ─────────────────────────────────────────────────

    @Test
    void generatesUuidWhenRequestIdHeaderMissing() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/test").build());

        run(filter, exchange);

        String requestId = exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void reusesInboundRequestId() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/test")
                        .header(RequestCorrelation.REQUEST_ID_HEADER, "existing-id-123")
                        .build());

        run(filter, exchange);

        assertEquals("existing-id-123",
                exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void generatesNewUuidForBlankInboundRequestId() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/test")
                        .header(RequestCorrelation.REQUEST_ID_HEADER, "   ")
                        .build());

        run(filter, exchange);

        String requestId = exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void storesRequestIdInExchangeAttributes() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/test").build());

        run(filter, exchange);

        assertNotNull(exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY));
    }

    @Test
    void putsRequestIdInReactorContext() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/test")
                        .header(RequestCorrelation.REQUEST_ID_HEADER, "ctx-id-123")
                        .build());

        List<String> capturedIds = new ArrayList<>();
        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            capturedIds.add(ctx.getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "MISSING"));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();

        assertEquals(List.of("ctx-id-123"), capturedIds);
    }

    // ── Response header presence ──────────────────────────────────────────────

    @Test
    void addsRequestIdToResponseHeaderOnSuccess() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/test").build());

        run(filter, exchange);

        assertNotNull(exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void addsRequestIdToResponseHeaderOnError() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/test").build());
        WebFilterChain errorChain = ex -> Mono.error(new RuntimeException("downstream error"));

        StepVerifier.create(filter.filter(exchange, errorChain))
                .expectError(RuntimeException.class)
                .verify();

        assertNotNull(exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    // ── Logging disabled ──────────────────────────────────────────────────────

    @Test
    void skipsLoggingWhenDisabled() {
        RequestLoggingProperties disabledProps = new RequestLoggingProperties();
        disabledProps.setEnabled(false);
        RequestLoggingWebFilter disabledFilter = new RequestLoggingWebFilter(disabledProps, sanitizer(), OBJECT_MAPPER);

        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/test").build());
        run(disabledFilter, exchange);

        // Still sets the header
        assertNotNull(exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
        // But does not log
        assertEquals(0, listAppender.list.size());
    }

    // ── JSON log format ───────────────────────────────────────────────────────

    @Test
    void requestStartLogIsValidJsonWithRequiredFields() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/notifications?env=DEV")
                        .header(RequestCorrelation.REQUEST_ID_HEADER, "req-123")
                        .build());

        run(filter, exchange);

        assertTrue(listAppender.list.size() >= 1);
        JsonNode startLog = parseJson(listAppender.list.get(0).getFormattedMessage());
        assertEquals("http.request.start", startLog.get("event").asText());
        assertEquals("req-123", startLog.get("requestId").asText());
        assertEquals("GET", startLog.get("method").asText());
        assertEquals("/api/v1/notifications", startLog.get("path").asText());
    }

    @Test
    void requestEndLogContainsStatusAndElapsedMs() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/notifications")
                        .header(RequestCorrelation.REQUEST_ID_HEADER, "req-456")
                        .build());

        run(filter, exchange);

        assertEquals(2, listAppender.list.size());
        JsonNode endLog = parseJson(listAppender.list.get(1).getFormattedMessage());
        assertEquals("http.request.end", endLog.get("event").asText());
        assertEquals("req-456", endLog.get("requestId").asText());
        assertTrue(endLog.has("status"));
        assertTrue(endLog.has("elapsedMs"));
    }

    @Test
    void requestErrorLogContainsExceptionTypeAndElapsedMs() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/v1/notifications")
                        .header(RequestCorrelation.REQUEST_ID_HEADER, "req-789")
                        .build());
        WebFilterChain errorChain = ex -> Mono.error(new IllegalStateException("downstream failure"));

        StepVerifier.create(filter.filter(exchange, errorChain))
                .expectError(IllegalStateException.class)
                .verify();

        assertEquals(2, listAppender.list.size());
        ILoggingEvent errorEvent = listAppender.list.get(1);
        assertEquals(Level.ERROR, errorEvent.getLevel());
        JsonNode errorLog = parseJson(errorEvent.getFormattedMessage());
        assertEquals("http.request.error", errorLog.get("event").asText());
        assertEquals("req-789", errorLog.get("requestId").asText());
        assertEquals("IllegalStateException", errorLog.get("exceptionType").asText());
        assertTrue(errorLog.has("elapsedMs"));
    }

    // ── Sanitization ──────────────────────────────────────────────────────────

    @Test
    void sanitizesQueryParams() {
        RequestLoggingProperties props = enabledProperties();
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.get("/api/test?policyNo=P001&env=DEV").build());

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("***"), "Sensitive query param should be masked");
    }

    // ── Body logging disabled by default ─────────────────────────────────────

    @Test
    void doesNotLogRequestBodyWhenBodyLoggingDisabled() {
        RequestLoggingProperties props = enabledProperties();
        // body-logging.enabled defaults to false

        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/notifications")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("{\"key\":\"value\"}"));

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertFalse(startMsg.contains("requestBody"), "Request body should not be logged when disabled");
    }

    @Test
    void doesNotLogRequestBodyWhenEndpointRuleEnabledButGlobalDisabled() {
        RequestLoggingProperties props = enabledProperties();
        // global body-logging disabled (default)
        RequestLoggingProperties.EndpointRule rule = new RequestLoggingProperties.EndpointRule();
        rule.setMethod("POST");
        rule.setPathPattern("/api/v1/notifications");
        rule.setLogRequestBody(true);
        props.getBodyLogging().getEndpoints().add(rule);
        // props.getBodyLogging().enabled = false (default)

        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/notifications")
                        .body("test body"));

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertFalse(startMsg.contains("requestBody"), "Body should not log when global body-logging is off");
    }

    @Test
    void logsRequestBodyWhenGlobalEnabledAndEndpointMatches() {
        RequestLoggingProperties props = enabledProperties();
        props.getBodyLogging().setEnabled(true);
        RequestLoggingProperties.EndpointRule rule = new RequestLoggingProperties.EndpointRule();
        rule.setMethod("POST");
        rule.setPathPattern("/api/v1/notifications");
        rule.setLogRequestBody(true);
        props.getBodyLogging().getEndpoints().add(rule);

        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/notifications")
                        .body("hello body"));

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("requestBody"), "Request body should be present in log");
        assertTrue(startMsg.contains("hello body"), "Request body content should appear in log");
    }

    @Test
    void doesNotLogBodyForNonMatchingEndpoint() {
        RequestLoggingProperties props = enabledProperties();
        props.getBodyLogging().setEnabled(true);
        RequestLoggingProperties.EndpointRule rule = new RequestLoggingProperties.EndpointRule();
        rule.setMethod("POST");
        rule.setPathPattern("/api/v1/other");
        rule.setLogRequestBody(true);
        props.getBodyLogging().getEndpoints().add(rule);

        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/notifications").body("some body"));

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertFalse(startMsg.contains("requestBody"), "No body log for non-matching endpoint");
    }

    @Test
    void truncatesBodyToMaxBodySizeBytes() {
        RequestLoggingProperties props = enabledProperties();
        props.getBodyLogging().setEnabled(true);
        RequestLoggingProperties.EndpointRule rule = new RequestLoggingProperties.EndpointRule();
        rule.setMethod("POST");
        rule.setPathPattern("/api/v1/test");
        rule.setLogRequestBody(true);
        rule.setMaxBodySizeBytes(10);
        props.getBodyLogging().getEndpoints().add(rule);

        String longBody = "A".repeat(100);
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/test").body(longBody));

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("[truncated]"), "Long body should be truncated");
        assertFalse(startMsg.contains("A".repeat(20)), "Body should be truncated");
    }

    @Test
    void endpointMaxBodySizeBytesOverridesDefault() {
        RequestLoggingProperties props = enabledProperties();
        props.getBodyLogging().setEnabled(true);
        props.getBodyLogging().setDefaultMaxBodySizeBytes(50);
        RequestLoggingProperties.EndpointRule rule = new RequestLoggingProperties.EndpointRule();
        rule.setMethod("POST");
        rule.setPathPattern("/api/v1/test");
        rule.setLogRequestBody(true);
        rule.setMaxBodySizeBytes(5); // endpoint override
        props.getBodyLogging().getEndpoints().add(rule);

        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/test").body("0123456789ABCDE"));

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("[truncated]"), "Should be truncated at endpoint limit of 5");
    }

    @Test
    void supportsWildcardPathPatterns() {
        RequestLoggingProperties props = enabledProperties();
        props.getBodyLogging().setEnabled(true);
        RequestLoggingProperties.EndpointRule rule = new RequestLoggingProperties.EndpointRule();
        rule.setMethod("POST");
        rule.setPathPattern("/api/v1/**");
        rule.setLogRequestBody(true);
        props.getBodyLogging().getEndpoints().add(rule);

        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/v1/notifications/read").body("body content"));

        run(new RequestLoggingWebFilter(props, sanitizer(), OBJECT_MAPPER), exchange);

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("requestBody"), "Wildcard pattern should match");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void run(RequestLoggingWebFilter f, MockServerWebExchange exchange) {
        WebFilterChain chain = ex -> Mono.empty();
        StepVerifier.create(f.filter(exchange, chain)).verifyComplete();
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private static RequestLoggingProperties enabledProperties() {
        RequestLoggingProperties props = new RequestLoggingProperties();
        props.setEnabled(true);
        return props;
    }

    private static LoggingSanitizer sanitizer() {
        LoggingSanitizerProperties sanitizerProps = new LoggingSanitizerProperties();
        sanitizerProps.setSensitiveTokens(List.of(
                "authorization", "token", "secret", "password", "apiKey",
                "Certificate", "clientSecret", "policyNo", "certNo", "userId",
                "privateKey", "publicKey", "policy-no", "cert-no", "user-id"));
        return new LoggingSanitizer(new ObjectMapper(), sanitizerProps);
    }

    private static JsonNode parseJson(String msg) {
        try {
            return OBJECT_MAPPER.readTree(msg);
        } catch (Exception e) {
            throw new AssertionError("Expected valid JSON but got: " + msg, e);
        }
    }
}
