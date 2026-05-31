package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequestHeaderContextWebFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesAllHeadersInReactorContextWhenPresent() {
        var filter = filter();
        var exchange = exchange(MockServerHttpRequest.get("/api/test")
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-123")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-123")
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, "zh-HK,zh;q=0.9,en;q=0.8")
                .build());
        AtomicReference<RequestHeaderContext> captured = new AtomicReference<>();

        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            captured.set((RequestHeaderContext) ctx.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertNotNull(captured.get());
        assertEquals("ACC-123", captured.get().accountRef());
        assertEquals("req-123", captured.get().requestId());
        assertEquals("zh-HK", captured.get().language());
        assertEquals("req-123", exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void allowsMissingAccountRef() {
        var filter = filter();
        var exchange = exchange(MockServerHttpRequest.get("/api/test")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-123")
                .header(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, "en")
                .build());
        AtomicBoolean proceeded = new AtomicBoolean(false);
        AtomicReference<RequestHeaderContext> captured = new AtomicReference<>();

        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            proceeded.set(true);
            captured.set((RequestHeaderContext) ctx.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertTrue(proceeded.get());
        assertNotNull(captured.get());
        assertNull(captured.get().accountRef());
        assertEquals("en", captured.get().language());
    }

    @Test
    void defaultsLanguageToEnglishWhenAcceptLanguageIsAbsent() {
        var filter = filter();
        var exchange = exchange(MockServerHttpRequest.get("/api/test")
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-123")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-123")
                .build());
        AtomicReference<RequestHeaderContext> captured = new AtomicReference<>();

        WebFilterChain chain = ex -> Mono.deferContextual(ctx -> {
            captured.set((RequestHeaderContext) ctx.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertNotNull(captured.get());
        assertEquals("en", captured.get().language());
    }

    @Test
    void generatesRequestIdWhenInboundHeaderIsMissing() {
        var filter = filter();
        var exchange = exchange(MockServerHttpRequest.get("/api/test").build());

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();

        String requestId = exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void reusesInboundRequestIdInResponseHeader() {
        var filter = filter();
        var exchange = exchange(MockServerHttpRequest.get("/api/test")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "existing-id-123")
                .build());

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();

        assertEquals("existing-id-123",
                exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
    }

    private static RequestHeaderContextWebFilter filter() {
        return new RequestHeaderContextWebFilter(
                new RequestLoggingWebFilter(new RequestLoggingProperties(), sanitizer(), OBJECT_MAPPER));
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private static LoggingSanitizer sanitizer() {
        LoggingSanitizerProperties sanitizerProps = new LoggingSanitizerProperties();
        sanitizerProps.setSensitiveTokens(List.of(
                "authorization", "token", "secret", "password", "apiKey",
                "Certificate", "clientSecret", "policyNo", "certNo", "userId",
                "privateKey", "publicKey", "policy-no", "cert-no", "user-id"));
        return new LoggingSanitizer(new ObjectMapper(), sanitizerProps);
    }
}