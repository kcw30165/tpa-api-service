package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.auth.login.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.session.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class StartLoginServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void startedReservationAuthenticatesOnceCreatesProtectedSessionAndBindsCsrf() {
        AtomicInteger calls = new AtomicInteger();
        CredentialAuthenticationPort credentials = c -> {
            calls.incrementAndGet();
            return Mono.just(new CredentialAuthenticationResult(CredentialAuthenticationStatus.AUTHENTICATED, "member",
                    "MBR", null, 0, List.of(new LoginCredentialDestination("email", "member@example.com")), null));
        };
        InMemorySessionPort sessions = new InMemorySessionPort();
        var lifecycle = new AuthFlowSessionLifecycleService(sessions,
                cmd -> new com.bct.ngtpa.apiservice.application.dto.session.SessionId("afs_test_value"),
                new SessionTtlCalculator(), Clock.fixed(NOW, ZoneOffset.UTC));
        var csrf = new AuthFlowSessionCsrfService(
                sessions,
                () -> "csrf_raw_value",
                new CsrfTokenHasher() {
                    public String hash(String value) {
                        return "csrf_hash_value";
                    }

                    public boolean matches(String raw, String stored) {
                        return "csrf_hash_value".equals(stored);
                    }
                });
        var service = service(credentials, lifecycle, csrf, coordinator(LoginIdempotencyStartOutcome.STARTED));
        StepVerifier
                .create(service.start(new StartLoginCommand(" Member ", "Exact", "en", "req-1", "login_key_opaque")))
                .assertNext(r -> {
                    assertThat(r.status()).isEqualTo(StartLoginStatus.STARTED);
                    assertThat(r.authFlowSessionId()).isEqualTo("afs_test_value");
                    assertThat(r.csrfToken()).isEqualTo("csrf_raw_value");
                    assertThat(r.destinations()).hasSize(1);
                    assertThat(r.destinations().get(0).channel()).isEqualTo("email");
                }).verifyComplete();
        assertThat(calls).hasValue(1);
        assertThat(sessions.current.data().attributes()).containsKeys("loginContext.keyVersion",
                "loginContext.algorithm", "loginContext.iv", "loginContext.ciphertext")
                .doesNotContainKeys("password", "normalizedUsername");
        assertThat(sessions.current.csrfTokenHash()).isEqualTo("csrf_hash_value");
    }

    @Test
    void nonStartedReservationDoesNotCallCredentialAuthorityOrCreateSession() {
        AtomicInteger calls = new AtomicInteger();
        CredentialAuthenticationPort credentials = c -> {
            calls.incrementAndGet();
            return Mono.error(new AssertionError());
        };
        InMemorySessionPort sessions = new InMemorySessionPort();
        var service = service(credentials,
                new AuthFlowSessionLifecycleService(sessions,
                        cmd -> new com.bct.ngtpa.apiservice.application.dto.session.SessionId("afs_unused"),
                        new SessionTtlCalculator(), Clock.fixed(NOW, ZoneOffset.UTC)),
                new AuthFlowSessionCsrfService(sessions), coordinator(LoginIdempotencyStartOutcome.COMPLETED_REPLAY));
        StepVerifier.create(service.start(new StartLoginCommand("member", "Exact", "en", "req", "login_key_opaque")))
                .assertNext(r -> assertThat(r.status()).isEqualTo(StartLoginStatus.COMPLETED_REPLAY)).verifyComplete();
        assertThat(calls).hasValue(0);
        assertThat(sessions.current).isNull();
    }

    private StartLoginService service(CredentialAuthenticationPort credentials,
            AuthFlowSessionLifecycleService lifecycle, AuthFlowSessionCsrfService csrf,
            LoginCredentialIdempotencyCoordinator idempotency) {
        var policy = new LoginCredentialPolicyService(true, LoginUsernameNormalization.ASCII_LOWERCASE, 1, 120,
                "^[A-Za-z0-9._-]+$", false, true, 1, 128);
        var factory = new LoginPreAuthContextFactory(() -> "dst_opaque_value", new LoginDestinationMasker());
        LoginPreAuthContextProtectorPort protector = new LoginPreAuthContextProtectorPort() {
            public com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext protect(
                    com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext c) {
                return new com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext(
                        "v1", "AES/GCM/NoPadding", "iv", "cipher");
            }

            public com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext unprotect(
                    com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext c) {
                throw new UnsupportedOperationException();
            }
        };
        return new StartLoginService(policy, new LoginCredentialRequestHasher(), idempotency, credentials, factory,
                protector, lifecycle, csrf, Clock.fixed(NOW, ZoneOffset.UTC), 900, true, "afs", 16);
    }

    private static final class InMemorySessionPort implements AuthFlowSessionPort {
        AuthFlowSession current;

        public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand c) {
            current = new AuthFlowSession(c.authFlowSessionId(), c.scenario(), c.initialStatus(), c.csrfTokenHash(),
                    c.activeOtpChallengeId(), c.data(), c.createdAt(), c.createdAt(), c.expiresAt());
            return Mono.just(current);
        }

        public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
            return Mono.just(Optional.ofNullable(current));
        }

        public Mono<AuthFlowSession> update(AuthFlowSession s) {
            current = s;
            return Mono.just(s);
        }

        public Mono<AuthFlowSession> touch(AuthFlowSessionId id, Instant at) {
            if (current == null)
                return Mono.empty();
            current = new AuthFlowSession(current.authFlowSessionId(), current.scenario(), current.status(),
                    current.csrfTokenHash(), current.activeOtpChallengeId(), current.data(), current.createdAt(), at,
                    current.expiresAt());
            return Mono.just(current);
        }

        public Mono<AuthFlowSession> complete(AuthFlowSessionId id, Instant at) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<AuthFlowSession> cancel(AuthFlowSessionId id, Instant at) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<Void> delete(AuthFlowSessionId id) {
            current = null;
            return Mono.empty();
        }
    }

    private LoginCredentialIdempotencyCoordinator coordinator(LoginIdempotencyStartOutcome outcome) {
        return new LoginCredentialIdempotencyCoordinator(
                new StubPort(outcome),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(300));
    }

    private static final class StubPort implements LoginCredentialIdempotencyPort {
        private final LoginIdempotencyStartOutcome outcome;

        StubPort(LoginIdempotencyStartOutcome outcome) {
            this.outcome = outcome;
        }

        public Mono<LoginIdempotencyStartResult> startProcessing(LoginIdempotencyStartCommand c) {
            LoginIdempotencyReplaySnapshot snapshot = outcome == LoginIdempotencyStartOutcome.COMPLETED_REPLAY
                    ? new LoginIdempotencyReplaySnapshot("STARTED", "commit_opaque")
                    : null;
            LoginIdempotencyStatus status = outcome == LoginIdempotencyStartOutcome.COMPLETED_REPLAY
                    ? LoginIdempotencyStatus.COMPLETED
                    : LoginIdempotencyStatus.PROCESSING;
            var record = new LoginIdempotencyRecord(c.action(), c.idempotencyKey(), c.requestHash(), status, snapshot,
                    NOW, NOW, NOW.plusSeconds(300));
            return Mono.just(new LoginIdempotencyStartResult(outcome, record));
        }

        public Mono<LoginIdempotencyRecord> markCompleted(LoginIdempotencyCompleteCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<LoginIdempotencyRecord> markFailed(LoginIdempotencyFailCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<LoginIdempotencyRecord> markUnknownOutcome(LoginIdempotencyUnknownOutcomeCommand c) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Mono<Optional<LoginIdempotencyRecord>> load(LoginIdempotencyAction a, LoginIdempotencyKey k) {
            return Mono.just(Optional.empty());
        }

        public Mono<Void> delete(LoginIdempotencyAction a, LoginIdempotencyKey k) {
            return Mono.empty();
        }
    }
}
