package com.bct.ngtpa.apiservice.adapter.in.web.filter;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.shared.web.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.*;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedPortalAccessContextWebFilterTest {
    private static final PortalAccessContext CONTEXT = new PortalAccessContext(new ActorContext("actor", "USER"),
            new AccountContext("acc_abcdefghijklmnopqrstuvwx", "DB", "00000008802", "0001", "DBA", "OE", null, null));

    @Test
    void resolvesUsingAccountRefAndSidXxx() {
        AtomicReference<String> account = new AtomicReference<>(), sid = new AtomicReference<>();
        PortalAccessContextPort port = new PortalAccessContextPort() {
            public Mono<PortalAccessContext> resolvePortalAccessContext(String ref) {
                return Mono.error(new AssertionError("one argument forbidden"));
            }

            public Mono<PortalAccessContext> resolvePortalAccessContext(String ref, String session) {
                account.set(ref);
                sid.set(session);
                return Mono.just(CONTEXT);
            }
        };
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/personal-information")
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "acc_abcdefghijklmnopqrstuvwx")
                .header(RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER, "sid_abcdefghijklmnopqrstuvwx"));
        AtomicReference<PortalAccessContext> reactor = new AtomicReference<>();
        WebFilterChain chain = e -> Mono.deferContextual(c -> {
            reactor.set(c.get(PortalAccessContextKeys.CONTEXT_KEY));
            return Mono.empty();
        });
        StepVerifier.create(new PortalAccessContextWebFilter(port).filter(exchange, chain)).verifyComplete();
        assertThat(account.get()).isEqualTo("acc_abcdefghijklmnopqrstuvwx");
        assertThat(sid.get()).isEqualTo("sid_abcdefghijklmnopqrstuvwx");
        assertThat((PortalAccessContext) exchange.getAttribute(PortalAccessContextKeys.ATTRIBUTE_KEY))
                .isEqualTo(CONTEXT);
        assertThat(reactor.get()).isEqualTo(CONTEXT);
    }

    @Test
    void accountRefWithoutSidFailsClosed() {
        PortalAccessContextPort port = ref -> Mono.just(CONTEXT);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/personal-information")
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "acc_abcdefghijklmnopqrstuvwx"));
        StepVerifier
                .create(new PortalAccessContextWebFilter(port).filter(exchange,
                        e -> Mono.error(new AssertionError("chain must not run"))))
                .expectError(AuthenticatedPortalContextResolutionException.class).verify();
    }

    @Test
    void absentAccountRefSkipsResolution() {
        PortalAccessContextPort port = ref -> Mono.error(new AssertionError("must not resolve"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/reference-data/countries"));
        StepVerifier.create(new PortalAccessContextWebFilter(port).filter(exchange, e -> Mono.empty()))
                .verifyComplete();
    }
}
