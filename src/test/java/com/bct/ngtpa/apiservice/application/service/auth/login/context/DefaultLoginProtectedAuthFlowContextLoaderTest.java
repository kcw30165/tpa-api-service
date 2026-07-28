package com.bct.ngtpa.apiservice.application.service.auth.login.context;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.session.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultLoginProtectedAuthFlowContextLoaderTest {
    static final Instant NOW = Instant.parse("2026-07-24T02:00:00Z");

    @Test
    void validatesCsrfLoginScenarioStartedStatusAndDecryptsContext() {
        var context = new LoginPreAuthContext("member01", "MBR", null, Map.of(), NOW.minusSeconds(1), "v1");
        var loader = loader(session(AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED), context, true);
        StepVerifier.create(loader.load(command())).assertNext(r -> {
            assertThat(r.status()).isEqualTo(LoginProtectedAuthFlowContextStatus.VALID);
            assertThat(r.context()).isSameAs(context);
        }).verifyComplete();
    }

    @Test
    void wrongScenarioOrStatusFailsBeforeDecryption() {
        for (var s : List.of(session(AuthFlowScenario.REGISTRATION, AuthFlowStatus.STARTED),
                session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT))) {
            StepVerifier.create(loader(s, null, true).load(command()))
                    .assertNext(r -> assertThat(r.isValid()).isFalse()).verifyComplete();
        }
    }

    @Test
    void invalidCsrfFailsWithoutLoadingProtectedContext() {
        StepVerifier
                .create(loader(session(AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED), null, false).load(command()))
                .assertNext(r -> assertThat(r.status()).isEqualTo(LoginProtectedAuthFlowContextStatus.CSRF_INVALID))
                .verifyComplete();
    }

    private LoginProtectedAuthFlowContextCommand command() {
        return new LoginProtectedAuthFlowContextCommand("afs_opaque", "csrf_opaque", "https://portal.example", null,
                "req-1", AuthFlowStatus.STARTED, false);
    }

    private DefaultLoginProtectedAuthFlowContextLoader loader(AuthFlowSession session, LoginPreAuthContext context,
            boolean csrfValid) {
        AuthFlowSessionPort port = new AuthFlowSessionPort() {
            public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand c) {
                return Mono.error(new UnsupportedOperationException());
            }

            public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
                return Mono.just(Optional.of(session));
            }

            public Mono<AuthFlowSession> update(AuthFlowSession s) {
                return Mono.just(s);
            }

            public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant n) {
                return Mono.just(session);
            }

            public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant n) {
                return Mono.just(session);
            }

            public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant n) {
                return Mono.just(session);
            }

            public Mono<Void> delete(AuthFlowSessionId id) {
                return Mono.empty();
            }
        };
        var lifecycle = new AuthFlowSessionLifecycleService(port, c -> null, new SessionTtlCalculator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var csrf = new AuthFlowSessionCsrfService(port, () -> "unused", new CsrfTokenHasher() {
            public String hash(String v) {
                return "hash";
            }

            public boolean matches(String r, String h) {
                return csrfValid;
            }
        });
        LoginPreAuthContextProtectorPort protector = new LoginPreAuthContextProtectorPort() {
            public LoginPreAuthEncryptedContext protect(LoginPreAuthContext c) {
                throw new UnsupportedOperationException();
            }

            public LoginPreAuthContext unprotect(LoginPreAuthEncryptedContext e) {
                if (context == null)
                    throw new AssertionError("must not decrypt");
                return context;
            }
        };
        return new DefaultLoginProtectedAuthFlowContextLoader(lifecycle, csrf, protector,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuthFlowSession session(AuthFlowScenario scenario, AuthFlowStatus status) {
        return new AuthFlowSession(new AuthFlowSessionId("afs_opaque"), scenario, status, "hash", null,
                new AuthFlowSessionData(null, null,
                        Map.of("loginContext.keyVersion", "v1", "loginContext.algorithm", "AES_GCM_V1",
                                "loginContext.iv", "iv", "loginContext.ciphertext", "cipher")),
                NOW.minusSeconds(10), NOW.minusSeconds(10), NOW.plusSeconds(300));
    }
}
