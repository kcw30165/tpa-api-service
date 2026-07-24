package com.bct.ngtpa.apiservice.application.service.auth.login.context;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend.*;
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

class DefaultLoginOtpResendContextLoaderTest {
    static final Instant NOW = Instant.parse("2026-07-24T05:00:00Z");

    @Test
    void acceptsOnlyOtpSentLoginSessionAndResolvesDestinationBoundToChallenge() {
        var destination = new LoginPreAuthDestination("dst", "mobile", "91234567", "****4567");
        var context = new LoginPreAuthContext("member", "MBR", null, Map.of("dst", destination), NOW.minusSeconds(10),
                "v1");
        var loader = loader(session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, "otp_active",
                new AuthFlowSessionData("mobile", "****4567", attributes())), context, true);
        StepVerifier.create(loader.load(command())).assertNext(r -> {
            assertThat(r.valid()).isTrue();
            assertThat(r.activeOtpChallengeId()).isEqualTo("otp_active");
            assertThat(r.channel()).isEqualTo(LoginOtpChannel.SMS);
            assertThat(r.destination()).isEqualTo("91234567");
        }).verifyComplete();
    }

    @Test
    void startedOrMissingChallengeFailsBeforeDecrypt() {
        for (var session : List.of(
                session(AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED, null,
                        new AuthFlowSessionData(null, null, attributes())),
                session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, null,
                        new AuthFlowSessionData("mobile", "****4567", attributes())))) {
            StepVerifier.create(loader(session, null, true).load(command()))
                    .assertNext(r -> assertThat(r.valid()).isFalse()).verifyComplete();
        }
    }

    @Test
    void invalidCsrfHasNoDecryption() {
        StepVerifier
                .create(loader(session(AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, "otp",
                        new AuthFlowSessionData("mobile", "****4567", attributes())), null, false).load(command()))
                .assertNext(r -> assertThat(r.status()).isEqualTo(LoginOtpResendContextStatus.CSRF_INVALID))
                .verifyComplete();
    }

    private LoginOtpResendContextCommand command() {
        return new LoginOtpResendContextCommand("afs", "csrf", "https://portal", null, "req");
    }

    private Map<String, String> attributes() {
        return Map.of("loginContext.keyVersion", "v1", "loginContext.algorithm", "AES_GCM_V1", "loginContext.iv", "iv",
                "loginContext.ciphertext", "cipher");
    }

    private DefaultLoginOtpResendContextLoader loader(AuthFlowSession s, LoginPreAuthContext context, boolean csrfOk) {
        AuthFlowSessionPort p = new Port(s);
        var lifecycle = new AuthFlowSessionLifecycleService(p, c -> null, new SessionTtlCalculator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var csrf = new AuthFlowSessionCsrfService(p, () -> "unused", new CsrfTokenHasher() {
            public String hash(String v) {
                return "hash";
            }

            public boolean matches(String r, String h) {
                return csrfOk;
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
        return new DefaultLoginOtpResendContextLoader(lifecycle, csrf, protector, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuthFlowSession session(AuthFlowScenario scenario, AuthFlowStatus status, String active,
            AuthFlowSessionData data) {
        return new AuthFlowSession(new AuthFlowSessionId("afs"), scenario, status, "hash", active, data,
                NOW.minusSeconds(10), NOW.minusSeconds(10), NOW.plusSeconds(300));
    }

    static final class Port implements AuthFlowSessionPort {
        final AuthFlowSession s;

        Port(AuthFlowSession s) {
            this.s = s;
        }

        public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
            return Mono.just(Optional.of(s));
        }

        public Mono<AuthFlowSession> update(AuthFlowSession v) {
            return Mono.just(v);
        }

        public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant n) {
            return Mono.just(s);
        }

        public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant n) {
            return Mono.just(s);
        }

        public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant n) {
            return Mono.just(s);
        }

        public Mono<Void> delete(AuthFlowSessionId id) {
            return Mono.empty();
        }
    }
}
