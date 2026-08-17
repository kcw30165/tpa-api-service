package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.*;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.shared.web.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PortalAccessContextWebFilterTest {
    private static final String SID = "sid_abcdefghijklmnopqrstuvwx";

    @Test
    void storesResolvedContextInExchangeAndReactorContext() {
        var expected = context("ACC-1");
        AtomicReference<String> resolved = new AtomicReference<>();
        PortalAccessContextPort port = new PortalAccessContextPort() {
            public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef) {
                return Mono.error(new AssertionError("one argument forbidden"));
            }
            public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef, String sessionId) {
                resolved.set(accountRef); return Mono.just(expected);
            }
        };
        var exchange = exchange("ACC-1");
        AtomicReference<PortalAccessContext> reactor = new AtomicReference<>();

        StepVerifier.create(filter(port).filter(exchange, current -> Mono.deferContextual(value -> {
                    reactor.set(value.get(PortalAccessContextKeys.CONTEXT_KEY)); return Mono.empty();
                })))
                .verifyComplete();

        assertThat(resolved.get()).isEqualTo("ACC-1");
        assertThat((PortalAccessContext) exchange.getAttribute(PortalAccessContextKeys.ATTRIBUTE_KEY))
                .isEqualTo(expected);
        assertThat(reactor.get()).isEqualTo(expected);
    }

    @Test
    void requestHeaderContextAccountRefTakesPrecedence() {
        var expected = context("ACC-CONTEXT");
        AtomicReference<String> resolved = new AtomicReference<>();
        PortalAccessContextPort port = new PortalAccessContextPort() {
            public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef) { return Mono.just(expected); }
            public Mono<PortalAccessContext> resolvePortalAccessContext(String accountRef, String sessionId) {
                resolved.set(accountRef); return Mono.just(expected);
            }
        };
        var exchange = exchange("ACC-HEADER");
        exchange.getAttributes().put(RequestHeaderContextKeys.ATTRIBUTE_KEY,
                new RequestHeaderContext("ACC-CONTEXT", "REQ-1", "en"));
        StepVerifier.create(filter(port).filter(exchange, current -> Mono.empty())).verifyComplete();
        assertThat(resolved.get()).isEqualTo("ACC-CONTEXT");
    }

    @Test
    void missingOrBlankAccountRefSkipsResolution() {
        AtomicInteger calls = new AtomicInteger();
        PortalAccessContextPort port = ref -> { calls.incrementAndGet(); return Mono.empty(); };
        for (String ref : new String[] {null, "   "}) {
            var request = MockServerHttpRequest.get("/api/v1/reference-data/countries");
            if (ref != null) request.header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ref);
            StepVerifier.create(filter(port).filter(MockServerWebExchange.from(request), current -> Mono.empty()))
                    .verifyComplete();
        }
        assertThat(calls).hasValue(0);
    }

    private PortalAccessContextWebFilter filter(PortalAccessContextPort port) {
        return new PortalAccessContextWebFilter(
                port,
                command -> Mono.just(authenticatedSession()),
                new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie()));
    }

    private MockServerWebExchange exchange(String accountRef) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/personal-information")
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, accountRef)
                .cookie(new HttpCookie("portal_sid", SID))
                .header("X-XSRF-TOKEN", "csrf_value"));
    }

    private AuthenticatedSession authenticatedSession() {
        return new AuthenticatedSession(
                        new AuthenticatedSessionId(SID), new ActorContext("actor", ActorUserRole.ROLE_USER_MBR), List.of(),
                AuthenticatedSessionStatus.ACTIVE, "csrf_hash",
                Instant.EPOCH, Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300), Instant.EPOCH.plusSeconds(900));
    }

    private PortalAccessContext context(String accountRef) {
        return new PortalAccessContext(new ActorContext("actor", ActorUserRole.ROLE_USER_MBR),
                new AccountContext(accountRef, "JP", "00000008802", "0001", "JPM", "OE", null, null));
    }
}
