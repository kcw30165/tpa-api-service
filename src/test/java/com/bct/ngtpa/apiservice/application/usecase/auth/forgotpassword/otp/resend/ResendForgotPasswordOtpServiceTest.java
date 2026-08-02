package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
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
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

        StepVerifier.create(fixture.service.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED);
                    assertThat(result.maskedDestination()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                    assertThat(result.resendAvailableInSeconds()).isNull();
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
    void missingSessionReturnsAuthFlowInvalidAndReleasesLock() {
        var fixture = new Fixture();
        fixture.sessions.snapshot = Optional.empty();

        StepVerifier.create(fixture.service.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID);
                    assertThat(result.maskedDestination()).isNull();
                    assertThat(result.expiresInSeconds()).isNull();
                    assertThat(result.resendAvailableInSeconds()).isNull();
                })
                .verifyComplete();

        assertThat(fixture.idempotency.attachPendingCount).isEqualTo(1);
        assertThat(fixture.locks.acquireCount).isEqualTo(1);
        assertThat(fixture.locks.releaseCount).isEqualTo(1);
        assertThat(fixture.sessions.loadSnapshotCount).isEqualTo(1);
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
        final ResendForgotPasswordOtpService service = new ResendForgotPasswordOtpService(
                idempotency,
                new SecureForgotPasswordOtpCommitRefGenerator(),
                locks,
                sessions,
                new MutableProtector(),
                command -> Mono.error(new AssertionError("foundation must not be called")),
                new UnsupportedOtpChallengePort(),
                new UnsupportedOtpChallengeStatePort(),
                new UnsupportedMessageDeliveryPort(),
                new ForgotPasswordDestinationMasker(),
                new ForgotPasswordOtpCommitCoordinator(
                        idempotency,
                        new UnsupportedCommitStatePort(),
                        new ForgotPasswordOtpCommitRetentionPolicy(Duration.ofMinutes(5), Duration.ofHours(1))),
                new ForgotPasswordOtpReplayResolver(
                        new UnsupportedCommitStatePort(),
                        sessions,
                        new UnsupportedOtpChallengeStatePort(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                ForgotPasswordOtpTemplates.approved(),
                ForgotPasswordInitialOtpPolicy.approved(),
                new ForgotPasswordOtpResendRequestHashGenerator(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                java.util.List.of("https://portal.example"));
    }

    private static final class MutableIdempotency implements ForgotPasswordIdempotencyPort {
        private ForgotPasswordIdempotencyStartOutcome outcome = ForgotPasswordIdempotencyStartOutcome.STARTED;
        private ForgotPasswordIdempotencyStartCommand lastStartCommand;
        private int attachPendingCount;

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
                    pendingRef);
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
        @Override
        public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
            throw new AssertionError("protect must not be called");
        }

        @Override
        public ForgotPasswordAuthFlowContext unprotect(ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
            throw new AssertionError("unprotect must not be called");
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

    private static final class UnsupportedOtpChallengeStatePort implements OtpChallengeStatePort {
        @Override
        public Mono<Void> save(com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge challenge) {
            return Mono.error(new AssertionError("save must not be called"));
        }

        @Override
        public Mono<Optional<com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge>> findById(String otpChallengeId) {
            return Mono.error(new AssertionError("findById must not be called"));
        }

        @Override
        public Mono<Void> update(com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge challenge) {
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