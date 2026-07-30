package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.service.session.SessionIdGenerator;
import com.bct.ngtpa.apiservice.application.service.session.SessionTtlCalculator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.LoadAuthFlowSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultForgotPasswordProtectedAuthFlowContextLoaderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void validSessionLoadsForgotPasswordContext() {
        var fixture = new Fixture(session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.IDENTITY_VERIFIED, false));
        fixture.createCsrf();

        StepVerifier.create(fixture.loader.load(command(Set.of(AuthFlowStatus.IDENTITY_VERIFIED), true)))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ForgotPasswordProtectedAuthFlowContextStatus.VALID);
                    assertThat(result.activeOtpChallengeId()).isEqualTo("otp-fp");
                    assertThat(result.context().maskedDestination()).isEqualTo("m****r@example.com");
                    assertThat(result.toString()).doesNotContain("member01", "tas-user-01", "member@example.com",
                            "otp-fp");
                })
                .verifyComplete();
    }

    @Test
    void rejectsMissingExpiredWrongScenarioInvalidStatusMissingContextAndDecryptFailure() {
        assertRejected(null, Set.of(AuthFlowStatus.IDENTITY_VERIFIED), false,
                ForgotPasswordProtectedAuthFlowContextStatus.MISSING_SESSION);
        assertRejected(session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.IDENTITY_VERIFIED, true),
                Set.of(AuthFlowStatus.IDENTITY_VERIFIED), false,
                ForgotPasswordProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION);
        assertRejected(session(AuthFlowScenario.LOGIN, AuthFlowStatus.IDENTITY_VERIFIED, false),
                Set.of(AuthFlowStatus.IDENTITY_VERIFIED), false,
                ForgotPasswordProtectedAuthFlowContextStatus.WRONG_SCENARIO);
        assertRejected(session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.STARTED, false),
                Set.of(AuthFlowStatus.IDENTITY_VERIFIED), false,
                ForgotPasswordProtectedAuthFlowContextStatus.INVALID_STATUS);

        var missingContext = session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.IDENTITY_VERIFIED, false);
        missingContext = new AuthFlowSession(
                missingContext.authFlowSessionId(),
                missingContext.scenario(),
                missingContext.status(),
                missingContext.csrfTokenHash(),
                missingContext.activeOtpChallengeId(),
                new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of()),
                missingContext.createdAt(),
                missingContext.updatedAt(),
                missingContext.expiresAt());
        assertRejected(missingContext, Set.of(AuthFlowStatus.IDENTITY_VERIFIED), false,
                ForgotPasswordProtectedAuthFlowContextStatus.MISSING_CONTEXT);

        var decryptFixture = new Fixture(
                session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.IDENTITY_VERIFIED, false));
        decryptFixture.protector.failDecrypt = true;
        StepVerifier.create(decryptFixture.loader.load(command(Set.of(AuthFlowStatus.IDENTITY_VERIFIED), false)))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(ForgotPasswordProtectedAuthFlowContextStatus.CONTEXT_DECRYPTION_FAILED))
                .verifyComplete();
    }

    @Test
    void rejectsInvalidCsrfWhenRequired() {
        var fixture = new Fixture(session(AuthFlowScenario.FORGOT_PASSWORD, AuthFlowStatus.IDENTITY_VERIFIED, false));
        fixture.createCsrf();

        StepVerifier.create(fixture.loader.load(new ForgotPasswordProtectedAuthFlowContextCommand(
                new AuthFlowSessionId("afs_fp"), "bad-token", null, null, "corr-1",
                Set.of(AuthFlowStatus.IDENTITY_VERIFIED), true)))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID))
                .verifyComplete();
    }

    private void assertRejected(AuthFlowSession session, Set<AuthFlowStatus> statuses, boolean csrfRequired,
            ForgotPasswordProtectedAuthFlowContextStatus expectedStatus) {
        var fixture = new Fixture(session);
        if (csrfRequired) {
            fixture.createCsrf();
        }

        StepVerifier.create(fixture.loader.load(command(statuses, csrfRequired)))
                .assertNext(result -> assertThat(result.status()).isEqualTo(expectedStatus))
                .verifyComplete();
    }

    private ForgotPasswordProtectedAuthFlowContextCommand command(Set<AuthFlowStatus> statuses, boolean csrfRequired) {
        return new ForgotPasswordProtectedAuthFlowContextCommand(
                new AuthFlowSessionId("afs_fp"), "csrf-token", null, null, "corr-1", statuses, csrfRequired);
    }

    private static AuthFlowSession session(AuthFlowScenario scenario, AuthFlowStatus status, boolean expired) {
        var now = CLOCK.instant();
        return new AuthFlowSession(
                new AuthFlowSessionId("afs_fp"),
                scenario,
                status,
                null,
                "otp-fp",
                new AuthFlowSessionData("EMAIL", "m****r@example.com", Map.of(
                        ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT, "ciphertext",
                        ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION, "v1",
                        ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM,
                        ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)),
                now,
                now,
                expired ? now.minusSeconds(1) : now.plusSeconds(300));
    }

    private static final class Fixture {
        private final FakeAuthFlowSessionPort port;
        private final AuthFlowSessionCsrfService csrfService;
        private final FakeContextProtector protector = new FakeContextProtector();
        private final DefaultForgotPasswordProtectedAuthFlowContextLoader loader;

        private Fixture(AuthFlowSession session) {
            this.port = new FakeAuthFlowSessionPort(session);
            var lifecycle = new AuthFlowSessionLifecycleService(port, new FixedSessionIdGenerator(),
                    new SessionTtlCalculator(), CLOCK);
            this.csrfService = new AuthFlowSessionCsrfService(port, new FixedCsrfTokenGenerator(),
                    new Sha256CsrfTokenHasher());
            this.loader = new DefaultForgotPasswordProtectedAuthFlowContextLoader(
                    lifecycle, csrfService, protector, CLOCK);
        }

        private void createCsrf() {
            if (port.session == null) {
                return;
            }
            StepVerifier.create(csrfService.createForSession(new CreateAuthFlowSessionCsrfCommand(
                    new AuthFlowSessionId("afs_fp"), CLOCK.instant(), 300, "corr-1")))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    private static final class FakeAuthFlowSessionPort implements AuthFlowSessionPort {
        private AuthFlowSession session;

        private FakeAuthFlowSessionPort(AuthFlowSession session) {
            this.session = session;
        }

        @Override
        public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            session = new AuthFlowSession(command.authFlowSessionId(), command.scenario(), command.initialStatus(),
                    command.csrfTokenHash(), command.activeOtpChallengeId(), command.data(), command.createdAt(),
                    command.createdAt(), command.expiresAt());
            return Mono.just(session);
        }

        @Override
        public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
            return Mono.just(Optional.ofNullable(session));
        }

        @Override
        public Mono<AuthFlowSession> update(AuthFlowSession session) {
            this.session = session;
            return Mono.just(session);
        }

        @Override
        public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.just(session);
        }

        @Override
        public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.just(session);
        }

        @Override
        public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
            return Mono.just(session);
        }

        @Override
        public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
            session = null;
            return Mono.empty();
        }
    }

    private static final class FixedSessionIdGenerator implements SessionIdGenerator {
        @Override
        public com.bct.ngtpa.apiservice.application.dto.session.SessionId generate(SessionIdGenerationCommand command) {
            return new com.bct.ngtpa.apiservice.application.dto.session.SessionId("afs_fp");
        }
    }

    private static final class FixedCsrfTokenGenerator implements CsrfTokenGenerator {
        @Override
        public String generate() {
            return "csrf-token";
        }
    }

    private static final class FakeContextProtector implements ForgotPasswordAuthFlowContextProtectorPort {
        private boolean failDecrypt;

        @Override
        public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
            return new ForgotPasswordAuthFlowEncryptedContext(
                    "ciphertext", "v1", ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
        }

        @Override
        public ForgotPasswordAuthFlowContext unprotect(ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
            if (failDecrypt) {
                throw new IllegalStateException("boom");
            }
            return new ForgotPasswordAuthFlowContext(
                    "member01",
                    "tas-user-01",
                    "ID",
                    true,
                    "reset-subject-01",
                    "EMAIL",
                    "member@example.com",
                    "m****r@example.com",
                    null,
                    "v1");
        }
    }
}