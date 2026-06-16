package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PortalAccessContextWebFilterTest {

    @Test
    void resolvesContextFromAccountRefHeaderAndStoresItInExchangeAndReactorContext() {
        PortalAccessContext expected = portalAccessContext("ACC-1", "JP", "JPM", "OE");
        AtomicInteger resolveCount = new AtomicInteger();
        PortalAccessContextWebFilter filter = new PortalAccessContextWebFilter(accountRef -> {
            resolveCount.incrementAndGet();
            assertEquals("ACC-1", accountRef);
            return Mono.just(expected);
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/personal-information")
                        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-1"));

        WebFilterChain chain = currentContextAssertingChain(expected);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(1, resolveCount.get());
        assertSame(expected, exchange.getAttributes().get(PortalAccessContextKeys.ATTRIBUTE_KEY));
    }

    @Test
    void prefersRequestHeaderContextAttributeWhenPresent() {
        PortalAccessContext expected = portalAccessContext("ACC-FROM-CONTEXT", "JP", "JPM", "OE");
        AtomicReference<String> resolvedAccountRef = new AtomicReference<>();
        PortalAccessContextWebFilter filter = new PortalAccessContextWebFilter(accountRef -> {
            resolvedAccountRef.set(accountRef);
            return Mono.just(expected);
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/personal-information")
                        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-FROM-HEADER"));
        exchange.getAttributes().put(
                RequestHeaderContextKeys.ATTRIBUTE_KEY,
                new RequestHeaderContext("ACC-FROM-CONTEXT", "REQ-1", "en"));

        StepVerifier.create(filter.filter(exchange, currentContextAssertingChain(expected)))
                .verifyComplete();

        assertEquals("ACC-FROM-CONTEXT", resolvedAccountRef.get());
    }

    @Test
    void missingAccountRefSkipsResolutionAndDoesNotFailFilterChain() {
        AtomicInteger resolveCount = new AtomicInteger();
        PortalAccessContextWebFilter filter = new PortalAccessContextWebFilter(accountRef -> {
            resolveCount.incrementAndGet();
            return Mono.error(new AssertionError("resolver must not be called when Account-Ref is missing"));
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));

        WebFilterChain chain = currentContextAbsentAssertingChain();

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(0, resolveCount.get());
        assertFalse(exchange.getAttributes().containsKey(PortalAccessContextKeys.ATTRIBUTE_KEY));
    }

    @Test
    void blankAccountRefSkipsResolutionAndDoesNotFailFilterChain() {
        AtomicInteger resolveCount = new AtomicInteger();
        PortalAccessContextWebFilter filter = new PortalAccessContextWebFilter(accountRef -> {
            resolveCount.incrementAndGet();
            return Mono.error(new AssertionError("resolver must not be called when Account-Ref is blank"));
        });
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/personal-information")
                        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "   "));

        StepVerifier.create(filter.filter(exchange, currentContextAbsentAssertingChain()))
                .verifyComplete();

        assertEquals(0, resolveCount.get());
    }

    @Test
    void propagatesResolutionFailure() {
        PortalAccessContextWebFilter filter = new PortalAccessContextWebFilter(accountRef -> Mono.error(
                new PortalAccessContextResolutionException(
                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                        "No context for " + accountRef)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/personal-information")
                        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-404"));

        StepVerifier.create(filter.filter(exchange, currentContextAbsentAssertingChain()))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof PortalAccessContextResolutionException);
                    assertEquals("No context for ACC-404", error.getMessage());
                })
                .verify();

        assertFalse(exchange.getAttributes().containsKey(PortalAccessContextKeys.ATTRIBUTE_KEY));
    }

    @Test
    void contextsArePerRequestAndDoNotLeak() {
        PortalAccessContext first = portalAccessContext("ACC-1", "JP", "JPM", "OE");
        PortalAccessContext second = portalAccessContext("ACC-2", "DB", "", "");
        PortalAccessContextWebFilter filter = new PortalAccessContextWebFilter(accountRef -> Mono.just(
                "ACC-1".equals(accountRef) ? first : second));

        MockServerWebExchange firstExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/personal-information")
                        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-1"));
        MockServerWebExchange secondExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/personal-information")
                        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "ACC-2"));

        StepVerifier.create(filter.filter(firstExchange, currentContextAssertingChain(first)))
                .verifyComplete();
        StepVerifier.create(filter.filter(secondExchange, currentContextAssertingChain(second)))
                .verifyComplete();

        assertSame(first, firstExchange.getAttributes().get(PortalAccessContextKeys.ATTRIBUTE_KEY));
        assertSame(second, secondExchange.getAttributes().get(PortalAccessContextKeys.ATTRIBUTE_KEY));
    }

    private static WebFilterChain currentContextAssertingChain(PortalAccessContext expected) {
        return exchange -> Mono.deferContextual(contextView -> {
            assertSame(expected, exchange.getAttributes().get(PortalAccessContextKeys.ATTRIBUTE_KEY));
            assertTrue(contextView.hasKey(PortalAccessContextKeys.CONTEXT_KEY));
            assertSame(expected, contextView.get(PortalAccessContextKeys.CONTEXT_KEY));
            return Mono.empty();
        });
    }

    private static WebFilterChain currentContextAbsentAssertingChain() {
        return exchange -> Mono.deferContextual(contextView -> {
            assertFalse(contextView.hasKey(PortalAccessContextKeys.CONTEXT_KEY));
            return Mono.empty();
        });
    }

    private static PortalAccessContext portalAccessContext(
            String accountRef,
            String accountEnv,
            String trustCode,
            String schemeType) {
        return new PortalAccessContext(
                new ActorContext("actor-001", "MEMBER"),
                new AccountContext(
                        accountRef,
                        accountEnv,
                        "policy-001",
                        "cert-001",
                        trustCode,
                        schemeType,
                        null,
                        null));
    }
}
