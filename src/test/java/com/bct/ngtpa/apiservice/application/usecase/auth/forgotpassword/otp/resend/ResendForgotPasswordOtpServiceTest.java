package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordDestinationMasker;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordInitialOtpPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpTemplates;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ResendForgotPasswordOtpServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T19:30:00Z");

    @Test
    void keyReusedWithDifferentRequestShortCircuitsBeforeLocking() {
        var fixture = new Fixture();
        fixture.idempotency.outcome = ForgotPasswordIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;

        StepVerifier.create(fixture.service().resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED);
                    assertThat(result.maskedDestination()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                    assertThat(result.resendCooldownSeconds()).isNull();
                    assertThat(result.remainingResendAttempts()).isNull();
                    assertThat(result.retryAfterSeconds()).isNull();
                })
                .verifyComplete();

        assertThat(fixture.idempotency.lastStartCommand).isNotNull();
        assertThat(fixture.idempotency.lastStartCommand.requestHash())
                .isEqualTo(new ForgotPasswordOtpResendRequestHashGenerator().generate());
        assertThat(fixture.idempotency.attachPendingCount).isZero();
        assertThat(fixture.locks.acquireCount).isZero();
        assertThat(fixture.locks.releaseCount).isZero();
        assertThat(fixture.sessions.loadSnapshotCount).isZero();
    }

    @Test
    void failedExistingReturnsPreviousFailedRecoveryOutcome() {
        var fixture = new Fixture();
        fixture.idempotency.outcome = ForgotPasswordIdempotencyStartOutcome.FAILED_EXISTING;

        StepVerifier.create(fixture.service().resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.PREVIOUS_FAILED);
                    assertThat(result.maskedDestination()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                    assertThat(result.resendCooldownSeconds()).isNull();
                    assertThat(result.remainingResendAttempts()).isNull();
                    assertThat(result.retryAfterSeconds()).isNull();
                })
                .verifyComplete();

        assertThat(fixture.idempotency.attachPendingCount).isZero();
        assertThat(fixture.locks.acquireCount).isZero();
        assertThat(fixture.locks.releaseCount).isZero();
        assertThat(fixture.sessions.loadSnapshotCount).isZero();
    }

    @Test
    void missingSessionReturnsAuthFlowInvalidAndReleasesLock() {
        var fixture = new Fixture();
        fixture.sessions.snapshot = Optional.empty();

        StepVerifier.create(fixture.service().resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID);
                    assertThat(result.maskedDestination()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                    assertThat(result.resendCooldownSeconds()).isNull();
                    assertThat(result.remainingResendAttempts()).isNull();
                    assertThat(result.retryAfterSeconds()).isNull();
                })
                .verifyComplete();

        assertThat(fixture.idempotency.attachPendingCount).isEqualTo(1);
        assertThat(fixture.locks.acquireCount).isEqualTo(1);
        assertThat(fixture.locks.releaseCount).isEqualTo(1);
        assertThat(fixture.sessions.loadSnapshotCount).isEqualTo(1);
    }

        @Test
        void missingChallengeLastSentAtFallsBackToCreatedAtAndReturnsCooldown() {
        var fixture = new Fixture();
        fixture.foundation = new ForgotPasswordOtpResendFoundationService(
            new ForgotPasswordOtpResendEligibilityValidator(
                new ForgotPasswordOtpResendPolicy(
                    ForgotPasswordInitialOtpPolicy.approved().resendCooldownSeconds(),
                    ForgotPasswordInitialOtpPolicy.approved().maxResendAttempts())));
        fixture.protector.context = new ForgotPasswordAuthFlowContext(
            "member",
            "customer-1",
            "tas-1",
            "HKID",
            true,
            "subject-1",
            "email",
            "member@example.com",
                "m*****@example.com",
            null,
            "v1",
            ForgotPasswordProtectedPhase.OTP_SENT);
        fixture.sessions.snapshot = Optional.of(new AuthFlowSessionSnapshot(
            new AuthFlowSession(
                new AuthFlowSessionId("afs_fp"),
                AuthFlowScenario.FORGOT_PASSWORD,
                AuthFlowStatus.OTP_SENT,
                new Sha256CsrfTokenHasher().hash("csrf_abc"),
                "otp-1",
                new AuthFlowSessionData(
                    "email",
                    "m*****@example.com",
                    Map.of(
                        "forgotPasswordOtpSuccessfulResendCount", "1",
                        "forgotPasswordContextCiphertext", "ciphertext",
                        "forgotPasswordContextKeyVersion", "v1",
                        "forgotPasswordContextAlgorithm", "AES_GCM_V1")),
                NOW.minusSeconds(300),
                NOW.minusSeconds(37),
                NOW.plusSeconds(300)),
            new SessionRevision("00000000000000000000000000000001")));
        fixture.challenges.challenge = Optional.of(new OtpChallenge(
            "otp-1",
            OtpScenario.FORGOT_PASSWORD,
            "afs_fp",
            OtpChannel.EMAIL,
                "b6e346dee08f8e8cf029179eb5177b5c2fc1a6e8ba01ab8ff4e1b8d56e89298c",
            "code",
            OtpStatus.CREATED,
            0,
            5,
            1,
            5,
            NOW.minusSeconds(37),
            NOW.plusSeconds(263),
            null,
            null,
            null));

        StepVerifier.create(fixture.service().resend(command()))
            .assertNext(result -> {
                assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE);
                    assertThat(result.maskedDestination()).isEqualTo("m*****@example.com");
                assertThat(result.expiresInSeconds()).isEqualTo(263);
                assertThat(result.resendCooldownSeconds()).isNull();
                assertThat(result.remainingResendAttempts()).isEqualTo(4);
                assertThat(result.retryAfterSeconds()).isEqualTo(23);
            })
            .verifyComplete();
        }

        @Test
        void duplicateInProgressWithStalePendingMarkersReplaysCurrentOtpState() {
        var fixture = new Fixture();
        fixture.idempotency.outcome = ForgotPasswordIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;
        fixture.idempotency.pendingOpaqueCommitRef = "pending-1";
        fixture.protector.context = new ForgotPasswordAuthFlowContext(
            "member",
            "customer-1",
            "tas-1",
            "HKID",
            true,
            "subject-1",
            "email",
            "member@example.com",
            "m*****@example.com",
            null,
            "v1",
            ForgotPasswordProtectedPhase.OTP_SENT);
        fixture.sessions.snapshot = Optional.of(new AuthFlowSessionSnapshot(
            new AuthFlowSession(
                new AuthFlowSessionId("afs_fp"),
                AuthFlowScenario.FORGOT_PASSWORD,
                AuthFlowStatus.OTP_SENT,
                new Sha256CsrfTokenHasher().hash("csrf_abc"),
                "otp-1",
                new AuthFlowSessionData(
                    "email",
                    "m*****@example.com",
                    Map.of(
                        "forgotPasswordOtpSuccessfulResendCount", "1",
                        "forgotPasswordContextCiphertext", "ciphertext",
                        "forgotPasswordContextKeyVersion", "v1",
                        "forgotPasswordContextAlgorithm", "AES_GCM_V1")),
                NOW.minusSeconds(300),
                NOW.minusSeconds(37),
                NOW.plusSeconds(300)),
            new SessionRevision("00000000000000000000000000000001")));
        fixture.challenges.challenge = Optional.of(new OtpChallenge(
            "otp-1",
            OtpScenario.FORGOT_PASSWORD,
            "afs_fp",
            OtpChannel.EMAIL,
            "b6e346dee08f8e8cf029179eb5177b5c2fc1a6e8ba01ab8ff4e1b8d56e89298c",
            "code",
            OtpStatus.CREATED,
            0,
            5,
            1,
            5,
            NOW.minusSeconds(37),
            NOW.plusSeconds(263),
            NOW.minusSeconds(37),
            null,
            null));

        StepVerifier.create(fixture.service().resend(command()))
            .assertNext(result -> {
                assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.COMPLETED_REPLAY);
                assertThat(result.maskedDestination()).isEqualTo("m*****@example.com");
                assertThat(result.expiresInSeconds()).isEqualTo(263);
                assertThat(result.remainingResendAttempts()).isEqualTo(4);
                assertThat(result.retryAfterSeconds()).isEqualTo(23);
            })
            .verifyComplete();
        }

    private static ResendForgotPasswordOtpCommand command() {
        return new ResendForgotPasswordOtpCommand(
                new AuthFlowSessionId("afs_fp"),
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/page",
                "req_abc",
                "en",
                "idem_abc");
    }

    private static final class Fixture {
        final MutableIdempotency idempotency = new MutableIdempotency();
        final MutableLocks locks = new MutableLocks();
        final MutableSessions sessions = new MutableSessions();
        final MutableProtector protector = new MutableProtector();
        final MutableOtpChallengeStatePort challenges = new MutableOtpChallengeStatePort();
        EvaluateForgotPasswordOtpResendUseCase foundation =
            command -> Mono.error(new AssertionError("foundation must not be called"));

        ResendForgotPasswordOtpService service() {
            return new ResendForgotPasswordOtpService(
                idempotency,
                new SecureForgotPasswordOtpCommitRefGenerator(),
                locks,
                sessions,
protector,
foundation,
new ForgotPasswordOtpResendIdempotencyClassifier(),
new UnsupportedOtpChallengePort(),
                challenges,
                new UnsupportedMessageDeliveryPort(),
                new ForgotPasswordDestinationMasker(),
                new ForgotPasswordOtpCommitCoordinator(
                    idempotency,
                    new UnsupportedCommitStatePort(),
                    new ForgotPasswordOtpCommitRetentionPolicy(Duration.ofMinutes(5), Duration.ofHours(1))),
                new ForgotPasswordOtpReplayResolver(
                    new UnsupportedCommitStatePort(),
                    sessions,
                    challenges,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    ForgotPasswordInitialOtpPolicy.approved().resendCooldownSeconds()),
                ForgotPasswordOtpTemplates.approved(),
                ForgotPasswordInitialOtpPolicy.approved(),
                new ForgotPasswordOtpResendRequestHashGenerator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                java.util.List.of("https://portal.example"));
        }
    }

    private static final class MutableIdempotency implements ForgotPasswordIdempotencyPort {
        private ForgotPasswordIdempotencyStartOutcome outcome = ForgotPasswordIdempotencyStartOutcome.STARTED;
        private ForgotPasswordIdempotencyStartCommand lastStartCommand;
        private int attachPendingCount;
        private String pendingOpaqueCommitRef;

        @Override
        public Mono<ForgotPasswordIdempotencyStartResult> startProcessing(ForgotPasswordIdempotencyStartCommand command) {
            lastStartCommand = command;
            return Mono.just(new ForgotPasswordIdempotencyStartResult(outcome, record(command, null)));
        }

        @Override
        public Mono<ForgotPasswordPendingCommitRefResult> attachPendingCommitRef(ForgotPasswordPendingCommitRefCommand command) {
            attachPendingCount++;
            return Mono.just(new ForgotPasswordPendingCommitRefResult(
                    ForgotPasswordPendingCommitRefResult.Status.ATTACHED,
                    command.opaqueCommitRef()));
        }

        @Override
        public Mono<ForgotPasswordIdempotencyRecord> markCompleted(ForgotPasswordIdempotencyCompleteCommand command) {
            return Mono.error(new AssertionError("markCompleted must not be called"));
        }

        @Override
        public Mono<ForgotPasswordIdempotencyRecord> markFailed(ForgotPasswordIdempotencyFailCommand command) {
            return Mono.error(new AssertionError("markFailed must not be called"));
        }

        @Override
        public Mono<ForgotPasswordIdempotencyRecord> markUnknownOutcome(ForgotPasswordIdempotencyUnknownOutcomeCommand command) {
            return Mono.error(new AssertionError("markUnknownOutcome must not be called"));
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

        private ForgotPasswordIdempotencyRecord record(ForgotPasswordIdempotencyStartCommand command, String pendingRef) {
            return new ForgotPasswordIdempotencyRecord(
                    command.action(),
                    command.idempotencyKey(),
                    command.requestHash(),
                    ForgotPasswordIdempotencyStatus.PROCESSING,
                    null,
                    command.now(),
                    command.now(),
                    command.expiresAt(),
                    pendingOpaqueCommitRef != null ? pendingOpaqueCommitRef : pendingRef);
        }
    }

    private static final class MutableLocks implements AuthFlowSessionMutationLockPort {
        private int acquireCount;
        private int releaseCount;

        @Override
        public Mono<SessionLockHandle> acquire(AuthFlowSessionId authFlowSessionId, Duration lease) {
            acquireCount++;
            return Mono.just(new SessionLockHandle(true, "lock-key", "owner"));
        }

        @Override
        public Mono<Boolean> release(SessionLockHandle handle) {
            releaseCount++;
            return Mono.just(true);
        }
    }

    private static final class MutableSessions implements AuthFlowSessionPort {
        private Optional<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot> snapshot = Optional.empty();
        private int loadSnapshotCount;

        @Override
        public Mono<Optional<com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot>> loadSnapshot(
                AuthFlowSessionId authFlowSessionId) {
            loadSnapshotCount++;
            return Mono.just(snapshot);
        }

        @Override
        public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
            return Mono.just(snapshot.map(com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot::session));
        }

        @Override
        public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
            return Mono.error(new UnsupportedOperationException());
        }
    }

    private static final class MutableProtector implements ForgotPasswordAuthFlowContextProtectorPort {
        private ForgotPasswordAuthFlowContext context;

        @Override
        public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
            throw new AssertionError("protect must not be called");
        }

        @Override
        public ForgotPasswordAuthFlowContext unprotect(ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
            if (context == null) {
                throw new AssertionError("unprotect context must be configured");
            }
            return context;
        }
    }

    private static final class UnsupportedOtpChallengePort implements OtpChallengePort {
        @Override
        public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
            return Mono.error(new AssertionError("createChallenge must not be called"));
        }

        @Override
        public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand command) {
            return Mono.error(new AssertionError("verifyChallenge must not be called"));
        }

        @Override
        public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand command) {
            return Mono.error(new AssertionError("replaceChallenge must not be called"));
        }

        @Override
        public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command) {
            return Mono.error(new AssertionError("invalidateChallenge must not be called"));
        }
    }

    private static final class MutableOtpChallengeStatePort implements OtpChallengeStatePort {
        private Optional<OtpChallenge> challenge = Optional.empty();

        @Override
        public Mono<Void> save(OtpChallenge challenge) {
            return Mono.error(new AssertionError("save must not be called"));
        }

        @Override
        public Mono<Optional<OtpChallenge>> findById(String otpChallengeId) {
            return Mono.just(challenge.filter(candidate -> candidate.otpChallengeId().equals(otpChallengeId)));
        }

        @Override
        public Mono<Void> update(OtpChallenge challenge) {
            return Mono.error(new AssertionError("update must not be called"));
        }

        @Override
        public Mono<Void> delete(String otpChallengeId) {
            return Mono.error(new AssertionError("delete must not be called"));
        }
    }

    private static final class UnsupportedMessageDeliveryPort implements MessageDeliveryPort {
        @Override
        public Mono<MessageDeliveryResult> deliver(MessageDeliveryCommand command) {
            return Mono.error(new AssertionError("deliver must not be called"));
        }
    }

    private static final class UnsupportedCommitStatePort implements ForgotPasswordOtpCommitStatePort {
        @Override
        public Mono<Boolean> saveIfAbsent(String opaqueCommitRef, com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState state, Duration ttl) {
            return Mono.error(new AssertionError("saveIfAbsent must not be called"));
        }

        @Override
        public Mono<Optional<com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState>> load(String opaqueCommitRef) {
            return Mono.error(new AssertionError("load must not be called"));
        }

        @Override
        public Mono<Void> delete(String opaqueCommitRef) {
            return Mono.error(new AssertionError("delete must not be called"));
        }
    }
}