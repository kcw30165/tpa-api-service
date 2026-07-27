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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedPortalAccessContextWebFilterTest {
    private static final String SID = "sid_abcdefghijklmnopqrstuvwx";
    private static final PortalAccessContext CONTEXT = new PortalAccessContext(
            new ActorContext("actor", "USER"),
            new AccountContext("acc_abcdefghijklmnopqrstuvwx", "DB", "00000008802", "0001", "DBA", "OE", null, null));

    @Test
    void resolvesUsingAccountRefPortalCookieAndAuthenticatedCsrf() {
        AtomicReference<String> account = new AtomicReference<>();
        AtomicReference<String> sid = new AtomicReference<>();
        PortalAccessContextPort port = new PortalAccessContextPort() {
            public Mono<PortalAccessContext> resolvePortalAccessContext(String ref) {
                return Mono.error(new AssertionError("one argument forbidden"));
            }
            public Mono<PortalAccessContext> resolvePortalAccessContext(String ref, String session) {
                account.set(ref); sid.set(session); return Mono.just(CONTEXT);
            }
        };
        var exchange = exchange(true, true, true);
        AtomicReference<PortalAccessContext> reactor = new AtomicReference<>();
        WebFilterChain chain = current -> Mono.deferContextual(context -> {
            reactor.set(context.get(PortalAccessContextKeys.CONTEXT_KEY));
            return Mono.empty();
        });

        StepVerifier.create(filter(port, validSession()).filter(exchange, chain)).verifyComplete();
        assertThat(account.get()).isEqualTo("acc_abcdefghijklmnopqrstuvwx");
        assertThat(sid.get()).isEqualTo(SID);
        assertThat((PortalAccessContext) exchange.getAttribute(PortalAccessContextKeys.ATTRIBUTE_KEY))
                .isEqualTo(CONTEXT);
        assertThat(reactor.get()).isEqualTo(CONTEXT);
    }

    @Test
    void accountRefWithoutPortalCookieOrCsrfFailsClosed() {
        PortalAccessContextPort port = ref -> Mono.just(CONTEXT);
        for (MockServerWebExchange exchange : List.of(
                exchange(true, false, true), exchange(true, true, false))) {
            StepVerifier.create(filter(port, validSession()).filter(exchange,
                            current -> Mono.error(new AssertionError("chain must not run"))))
                    .expectError(AuthenticatedPortalContextResolutionException.class)
                    .verify();
        }
    }

    @Test
    void absentAccountRefSkipsAuthenticationResolution() {
        PortalAccessContextPort port = ref -> Mono.error(new AssertionError("must not resolve"));
        StepVerifier.create(filter(port, command -> Mono.error(new AssertionError("must not validate")))
                        .filter(exchange(false, false, false), current -> Mono.empty()))
                .verifyComplete();
    }

    private PortalAccessContextWebFilter filter(
            PortalAccessContextPort port,
            ValidateAuthenticatedSessionUseCase validation) {
        return new PortalAccessContextWebFilter(
                port, validation,
                new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie()));
    }

    private ValidateAuthenticatedSessionUseCase validSession() {
        return command -> Mono.just(new AuthenticatedSession(
                new AuthenticatedSessionId(SID), CONTEXT.actor(), List.of(),
                AuthenticatedSessionStatus.ACTIVE, "csrf_hash",
                Instant.EPOCH, Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300), Instant.EPOCH.plusSeconds(900)));
    }

    private MockServerWebExchange exchange(boolean account, boolean sid, boolean csrf) {
        var request = MockServerHttpRequest.get("/api/v1/personal-information");
        if (account) request.header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, "acc_abcdefghijklmnopqrstuvwx");
        if (sid) request.cookie(new HttpCookie("portal_sid", SID));
        if (csrf) request.header("X-XSRF-TOKEN", "csrf_value");
        return MockServerWebExchange.from(request);
    }
}
