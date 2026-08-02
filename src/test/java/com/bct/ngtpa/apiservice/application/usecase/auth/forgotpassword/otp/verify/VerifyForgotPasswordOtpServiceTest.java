package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextCommand;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class VerifyForgotPasswordOtpServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T18:00:00Z");

    @Test
    void verifiedOtpPromotesSessionToResetAuthorizedAndStoresServerSideAuthorizationOnly() {
        var fixture = new Fixture(OtpVerificationOutcome.VALID, 4);

        StepVerifier.create(fixture.service.verify(new VerifyForgotPasswordOtpCommand(
                new AuthFlowSessionId("afs_fp"),
                "csrf_abc",
                "123456",
                "https://portal.example",
                "https://portal.example/page",
                "idem_abc",
                "en",
                "req_abc",
                Duration.ofSeconds(300))))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyForgotPasswordOtpStatus.VERIFIED);
                    assertThat(result.nextStep())
                            .isEqualTo(com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep.PASSWORD_SETUP);
                })
                .verifyComplete();

        assertThat(fixture.updatedSession).isNotNull();
        assertThat(fixture.updatedSession.status()).isEqualTo(AuthFlowStatus.RESET_AUTHORIZED);
        assertThat(fixture.updatedSession.activeOtpChallengeId()).isNull();
        assertThat(fixture.refreshCount).isEqualTo(1);
        assertThat(fixture.completedCount).isEqualTo(1);
        assertThat(fixture.failedCount).isZero();

        ForgotPasswordResetAuthorization authorization = fixture.protectedContext.resetAuthorization();
        assertThat(authorization).isNotNull();
        assertThat(authorization.authFlowSessionId()).isEqualTo("afs_fp");
        assertThat(authorization.resetSubjectId()).isEqualTo("reset-subject-01");
        assertThat(authorization.issuedAt()).isEqualTo(NOW);
        assertThat(authorization.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(authorization.consumedAt()).isNull();
    }

    private static final class Fixture {
        final MutableProtector protector = new MutableProtector();
        final MutableSessions sessions = new MutableSessions();
        final MutableIdempotency idempotency = new MutableIdempotency();
        final VerifyForgotPasswordOtpService service;
        AuthFlowSession updatedSession;
        ForgotPasswordAuthFlowContext protectedContext;
        int refreshCount;
        int completedCount;
        int failedCount;

        Fixture(OtpVerificationOutcome outcome, int remainingAttempts) {
            ForgotPasswordAuthFlowContext context = new ForgotPasswordAuthFlowContext(
                    "member01",
                    "cust-db01",
                    "tas-user-01",
                    "ID",
                    true,
                    "reset-subject-01",
                    "EMAIL",
                    "member@example.com",
                    "m****r@example.com",
                    null,
                    "v1",
                    ForgotPasswordProtectedPhase.OTP_SENT);
            protector.context = context;
            protectedContext = context;

            AuthFlowSession session = new AuthFlowSession(
                    new AuthFlowSessionId("afs_fp"),
                    AuthFlowScenario.FORGOT_PASSWORD,
                    AuthFlowStatus.STARTED,
                    "csrf_hash",
                    "otp_active",
                    new AuthFlowSessionData("EMAIL", "m****r@example.com", new LinkedHashMap<>()),
                    NOW.minusSeconds(120),
                    NOW.minusSeconds(60),
                    NOW.plusSeconds(1800));
            sessions.session = session;

            ForgotPasswordProtectedAuthFlowContextLoader loader = command -> Mono.just(
                    ForgotPasswordProtectedAuthFlowContextResult.valid("otp_active", protector.context));
            AuthFlowSessionMutationLockPort locks = new AuthFlowSessionMutationLockPort() {
                @Override
                public Mono<SessionLockHandle> acquire(AuthFlowSessionId sessionId, Duration lease) {
                    return Mono.just(new SessionLockHandle(true, "lock-key", "owner"));
                }

                @Override
                public Mono<Boolean> release(SessionLockHandle handle) {
                    return Mono.just(true);
                }
            };
            AuthFlowSessionExpiryRefreshPort expiry = (sessionId, interactionAt) -> {
                refreshCount++;
                return Mono.just(updatedSession == null ? sessions.session : updatedSession);
            };
            OtpChallengePort otp = new OtpChallengePort() {
                @Override
                public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand command) {
                    return Mono.just(new VerifyOtpChallengeResult(command.otpChallengeId(), outcome, remainingAttempts, null));
                }

                @Override
                public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
                    return Mono.error(new UnsupportedOperationException());
                }

                @Override
                public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand command) {
                    return Mono.error(new UnsupportedOperationException());
                }

                @Override
                public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command) {
                    return Mono.empty();
                }
            };

            service = new VerifyForgotPasswordOtpService(
                    loader,
                    protector,
                    sessions,
                    locks,
                    expiry,
                    otp,
                    idempotency,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(300));
        }

        private final class MutableSessions implements AuthFlowSessionPort {
            private AuthFlowSession session;

            @Override
            public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
                return Mono.just(Optional.of(session));
            }

            @Override
            public Mono<Optional<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot>> loadSnapshot(
                    AuthFlowSessionId authFlowSessionId) {
                return Mono.just(Optional.of(new com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot(
                        session,
                        new com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision(
                                "11111111111111111111111111111111"))));
            }

            @Override
            public Mono<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult> updateRevisioned(
                    com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand command) {
                updatedSession = command.updated();
                session = updatedSession;
                return Mono.just(new com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult(
                        com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome.APPLIED,
                        new com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot(
                                updatedSession,
                                new com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision(
                                        "22222222222222222222222222222222"))));
            }

            @Override
            public Mono<AuthFlowSession> create(
                    com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand command) {
                return Mono.error(new UnsupportedOperationException());
            }
        }

        private final class MutableProtector implements ForgotPasswordAuthFlowContextProtectorPort {
            private ForgotPasswordAuthFlowContext context;

            @Override
            public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
                this.context = context;
                protectedContext = context;
                return new ForgotPasswordAuthFlowEncryptedContext("ciphertext", "v1",
                        ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
            }

            @Override
            public ForgotPasswordAuthFlowContext unprotect(ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
                return context;
            }
        }

        private final class MutableIdempotency implements ForgotPasswordIdempotencyPort {
            @Override
            public Mono<ForgotPasswordIdempotencyStartResult> startProcessing(ForgotPasswordIdempotencyStartCommand command) {
                return Mono.just(new ForgotPasswordIdempotencyStartResult(
                        ForgotPasswordIdempotencyStartOutcome.STARTED,
                        new ForgotPasswordIdempotencyRecord(
                                command.action(),
                                command.idempotencyKey(),
                                command.requestHash(),
                                ForgotPasswordIdempotencyStatus.PROCESSING,
                                null,
                                command.now(),
                                command.now(),
                                command.expiresAt(),
                                null)));
            }

            @Override
            public Mono<ForgotPasswordIdempotencyRecord> markCompleted(ForgotPasswordIdempotencyCompleteCommand command) {
                completedCount++;
                return Mono.just(new ForgotPasswordIdempotencyRecord(
                        command.action(),
                        command.idempotencyKey(),
                        command.requestHash(),
                        ForgotPasswordIdempotencyStatus.COMPLETED,
                        command.replaySnapshot(),
                    command.completedAt(),
                    command.completedAt(),
                    command.completedAt().plus(command.ttl()),
                        null));
            }

            @Override
            public Mono<ForgotPasswordIdempotencyRecord> markFailed(ForgotPasswordIdempotencyFailCommand command) {
                failedCount++;
                return Mono.just(new ForgotPasswordIdempotencyRecord(
                        command.action(),
                        command.idempotencyKey(),
                        command.requestHash(),
                        ForgotPasswordIdempotencyStatus.FAILED,
                        null,
                        command.failedAt(),
                        command.failedAt(),
                        command.failedAt().plus(command.ttl()),
                        null));
            }

            @Override
            public Mono<ForgotPasswordIdempotencyRecord> markUnknownOutcome(
                    ForgotPasswordIdempotencyUnknownOutcomeCommand command) {
                return Mono.error(new UnsupportedOperationException());
            }

            @Override
            public Mono<Optional<ForgotPasswordIdempotencyRecord>> load(
                    ForgotPasswordIdempotencyAction action,
                    ForgotPasswordIdempotencyKey key) {
                return Mono.just(Optional.empty());
            }

            @Override
            public Mono<Void> delete(ForgotPasswordIdempotencyAction action, ForgotPasswordIdempotencyKey key) {
                return Mono.empty();
            }
        }
    }
}