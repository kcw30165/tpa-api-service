package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordPendingCommitRefResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordDestinationMasker;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpBindingResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter;
import java.time.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest {
  @Test
  void calculatedLeaseIncludesEveryBound() {
    var p = new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000);
    assertThat(p.minimumLeaseMillis()).isEqualTo(55200);
    assertThat(p.lease()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void tenSecondsRejected() {
    assertThatThrownBy(
            () -> new ForgotPasswordDetailsOtpLeasePolicy(10, 10, 10, 3, 10000, 100, 5000, 1000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void upperBoundsRejected() {
    assertThatThrownBy(
            () -> new ForgotPasswordDetailsOtpLeasePolicy(121, 10, 10, 3, 10000, 100, 5000, 1000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new ForgotPasswordDetailsOtpLeasePolicy(60, 31, 10, 3, 10000, 100, 5000, 1000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void monotonicBudgetFailsClosed() {
    var b = new ForgotPasswordMutationLeaseBudget(Duration.ofSeconds(60), () -> 0L);
    assertThat(b.hasPersistenceBudget(Duration.ofSeconds(10))).isTrue();
  }

  @Test
  void matchedEmailDetailsIssueOtpInsteadOfSystemError() {
    Instant now = Instant.parse("2026-08-04T10:15:30Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    AuthFlowSessionId sessionId = new AuthFlowSessionId("afs_test_123");
    SessionLockHandle lock = new SessionLockHandle(true, "lock-key", "owner-token");
    ForgotPasswordIdempotencyRecord processing =
        new ForgotPasswordIdempotencyRecord(
            ForgotPasswordIdempotencyAction.DETAILS_AND_OTP,
            new ForgotPasswordIdempotencyKey("ac2e9b54-836d-4729-8957-d89b1d8ae015"),
            new ForgotPasswordRequestHash(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
            ForgotPasswordIdempotencyStatus.PROCESSING,
            null,
            now,
            now,
            now.plusSeconds(300));
    ForgotPasswordAuthFlowContext context =
        new ForgotPasswordAuthFlowContext(
            "member-user",
            "customer-id",
            "tas-user-id",
            "HKID",
            true,
            "reset-subject-id",
            null,
            null,
            null,
            new ForgotPasswordResetAuthorization(
                "reset-token",
                sessionId.value(),
                "reset-subject-id",
                now,
                now.plusSeconds(600),
                null),
            "v1",
            ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);

    ForgotPasswordIdempotencyPort idempotency = mock(ForgotPasswordIdempotencyPort.class);
    when(idempotency.startProcessing(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordIdempotencyStartResult(
                    ForgotPasswordIdempotencyStartOutcome.STARTED, processing)));
    when(idempotency.attachPendingCommitRef(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordPendingCommitRefResult(
                    ForgotPasswordPendingCommitRefResult.Status.ATTACHED, "opaque-ref-1")));
    when(idempotency.markCompleted(any()))
        .thenAnswer(
            invocation -> {
              var command =
                  invocation.getArgument(
                      0,
                      com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency
                          .ForgotPasswordIdempotencyCompleteCommand.class);
              return Mono.just(
                  new ForgotPasswordIdempotencyRecord(
                      processing.action(),
                      processing.idempotencyKey(),
                      processing.requestHash(),
                      ForgotPasswordIdempotencyStatus.COMPLETED,
                      command.replaySnapshot(),
                      processing.createdAt(),
                      command.completedAt(),
                      processing.expiresAt(),
                      processing.pendingOpaqueCommitRef()));
            });
    when(idempotency.markFailed(any())).thenReturn(Mono.just(processing));
    when(idempotency.markUnknownOutcome(any())).thenReturn(Mono.just(processing));

    AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
    when(locks.acquire(any(), any())).thenReturn(Mono.just(lock));
    when(locks.release(lock)).thenReturn(Mono.just(true));

    AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
    when(sessions.loadSnapshot(sessionId))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new AuthFlowSessionSnapshot(
                        new AuthFlowSession(
                            sessionId,
                            AuthFlowScenario.FORGOT_PASSWORD,
                            AuthFlowStatus.IDENTITY_VERIFIED,
                            "csrf-hash",
                            null,
                            new AuthFlowSessionData(
                                null,
                                null,
                                Map.of(
                                    "forgotPasswordContextCiphertext", "ciphertext",
                                    "forgotPasswordContextKeyVersion", "v1",
                                    "forgotPasswordContextAlgorithm", "AES_GCM_V1")),
                            now.minusSeconds(30),
                            now.minusSeconds(5),
                            now.plusSeconds(300)),
                        new SessionRevision("0123456789abcdef0123456789abcdef")))));

    ForgotPasswordAuthFlowContextProtectorPort protector =
        mock(ForgotPasswordAuthFlowContextProtectorPort.class);
    when(protector.unprotect(any(ForgotPasswordAuthFlowEncryptedContext.class)))
        .thenReturn(context);

    ForgotPasswordDetailsLookupPort lookup = mock(ForgotPasswordDetailsLookupPort.class);
    when(lookup.verify(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.MATCHED)));

    OtpChallengePort otp = mock(OtpChallengePort.class);
    when(otp.createChallenge(any()))
        .thenReturn(
            Mono.just(new CreateOtpChallengeResult("otp-1", "123456", now.plusSeconds(300), 300)));

    EmailSenderPort emailSender = mock(EmailSenderPort.class);
    when(emailSender.sendEmail(any()))
        .thenReturn(Mono.just(new SenderResult(SenderStatus.ACCEPTED, "provider-1", null, null)));

    ForgotPasswordOtpSessionBindingPort binding = mock(ForgotPasswordOtpSessionBindingPort.class);
    when(binding.apply(any()))
        .thenAnswer(
            invocation -> {
              var command =
                  invocation.getArgument(
                      0,
                      com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                          .ForgotPasswordOtpBindingCommand.class);
              return Mono.just(
                  command.deliveryStatus()
                          == com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus
                              .ACCEPTED
                      ? new ForgotPasswordOtpBindingResult(
                          ForgotPasswordOtpBindingResult.Status.BOUND)
                      : new ForgotPasswordOtpBindingResult(
                          ForgotPasswordOtpBindingResult.Status.CONFIRMED_FAILED));
            });

    ForgotPasswordOtpCommitStatePort commitStates = new InMemoryCommitStates();
    ForgotPasswordOtpCommitCoordinator commits =
        new ForgotPasswordOtpCommitCoordinator(
            idempotency,
            commitStates,
            new ForgotPasswordOtpCommitRetentionPolicy(
                Duration.ofSeconds(60), Duration.ofMinutes(10)));

    ForgotPasswordDetailsOtpOrchestrationService service =
        new ForgotPasswordDetailsOtpOrchestrationService(
            idempotency,
            new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                .SecureForgotPasswordOtpCommitRefGenerator(),
            locks,
            sessions,
            protector,
            new ForgotPasswordUnderLockDetailsVerifier(lookup),
            otp,
            new MessageDeliveryAdapter(mock(SmsSenderPort.class), emailSender),
            new ForgotPasswordDestinationMasker(),
            binding,
            commits,
            mock(ForgotPasswordOtpReplayResolver.class),
            new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000),
            clock,
            Duration.ofMinutes(5));

    StepVerifier.create(
            service.process(
                new ForgotPasswordDetailsOtpCommand(
                    sessionId,
                    "csrf",
                    "https://portal.example",
                    "https://portal.example/forgot-password",
                    "req-123",
                    "en",
                    "ac2e9b54-836d-4729-8957-d89b1d8ae015",
                    "04/07/1978",
                    "HULLCS.QGLYBCY@UC.KJI",
                    null)))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
              assertThat(result.maskedDestination()).isEqualTo("H*************@UC.KJI");
              assertThat(result.expiresInSeconds()).isEqualTo(300);
              assertThat(result.resendAvailableInSeconds()).isEqualTo(60);
            })
        .verifyComplete();
  }

  private static final class InMemoryCommitStates implements ForgotPasswordOtpCommitStatePort {
    private final java.util.Map<String, ForgotPasswordOtpCommitState> states =
        new java.util.HashMap<>();

    @Override
    public Mono<Boolean> saveIfAbsent(
        String opaqueCommitRef, ForgotPasswordOtpCommitState state, Duration ttl) {
      return Mono.just(states.putIfAbsent(opaqueCommitRef, state) == null);
    }

    @Override
    public Mono<Optional<ForgotPasswordOtpCommitState>> load(String opaqueCommitRef) {
      return Mono.just(Optional.ofNullable(states.get(opaqueCommitRef)));
    }

    @Override
    public Mono<Void> delete(String opaqueCommitRef) {
      states.remove(opaqueCommitRef);
      return Mono.empty();
    }
  }
}
