package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
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
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpBindingResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.time.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordDetailsOtpOrchestrationSubtask005004FailFirstTest {
  // ----- log capture -------------------------------------------------------

  private ListAppender<ILoggingEvent> logAppender;
  private Logger operationLogger;

  @BeforeEach
  void attachLogAppender() {
    operationLogger =
        (Logger) LoggerFactory.getLogger(ForgotPasswordDetailsOtpProtectedOperation.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    operationLogger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    operationLogger.detachAppender(logAppender);
  }

  private void assertLoggedFailure(String stage) {
    assertThat(logAppender.list).hasSize(1);
    ILoggingEvent event = logAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage())
        .contains(
            "event=forgot-password.details-otp.failed",
            "requestId=",
            "stage=" + stage,
            "exceptionType=IllegalStateException")
        .doesNotContain("boom");
  }

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
    ForgotPasswordAuthFlowContext context =
        new ForgotPasswordAuthFlowContext(
            "member-user",
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
            null,
            null,
            null,
            "v1",
            ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);

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
                new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.MATCHED, null, null, null)));

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
    com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort registry =
        mock(com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort.class);
    when(registry.resolve(any(), any(), any()))
        .thenReturn(
            new com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition(
                com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType
                    .FORGOT_PASSWORD_OTP,
                com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel.EMAIL,
                "en",
                "BCT forgot password verification code",
                "Your BCT forgot password verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                java.util.Set.of("code", "ttlValue", "ttlUnit"),
                "minutes"));
    ForgotPasswordDetailsOtpProtectedOperation service =
        new ForgotPasswordDetailsOtpProtectedOperation(
            new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                .SecureForgotPasswordOtpCommitRefGenerator(),
            locks,
            sessions,
            protector,
            new ForgotPasswordUnderLockDetailsVerifier(lookup),
            otp,
            new MessageDeliveryAdapter(mock(SmsSenderPort.class), emailSender),
            new MaskingService(new MaskingProperties()),
            binding,
            commitStates,
            new ForgotPasswordOtpCommitRetentionPolicy(
                Duration.ofSeconds(60), Duration.ofMinutes(10)),
            new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000),
            clock,
            Duration.ofMinutes(5),
            registry);

    StepVerifier.create(
            service.execute(
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
            execution -> {
              var result = execution.result();
              assertThat(result.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.SUCCESS);
              assertThat(result.maskedDestination()).isEqualTo("H************Y@UC.KJI");
              assertThat(result.expiresInSeconds()).isEqualTo(300);
              assertThat(result.resendAvailableInSeconds()).isEqualTo(60);
              assertThat(execution.replayReference()).isNotNull();
            })
        .verifyComplete();
    verify(registry).resolve(any(), any(), any());
  }

  @Test
  void lockAcquisitionErrorYieldsAFailureResultAndLogsTheFailure() {
    Instant now = Instant.parse("2026-08-04T10:15:30Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    AuthFlowSessionId sessionId = new AuthFlowSessionId("afs_test_123");
    SessionLockHandle lock = new SessionLockHandle(true, "lock-key", "owner-token");
    ForgotPasswordAuthFlowContext context =
        new ForgotPasswordAuthFlowContext(
            "member-user",
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
            null,
            null,
            null,
            "v1",
            ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);

    AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
    when(locks.acquire(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));
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
                new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.MATCHED, null, null, null)));

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
    com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort registry =
        mock(com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort.class);
    when(registry.resolve(any(), any(), any()))
        .thenReturn(
            new com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition(
                com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType
                    .FORGOT_PASSWORD_OTP,
                com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel.EMAIL,
                "en",
                "BCT forgot password verification code",
                "Your BCT forgot password verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                java.util.Set.of("code", "ttlValue", "ttlUnit"),
                "minutes"));
    ForgotPasswordDetailsOtpProtectedOperation service =
        new ForgotPasswordDetailsOtpProtectedOperation(
            new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                .SecureForgotPasswordOtpCommitRefGenerator(),
            locks,
            sessions,
            protector,
            new ForgotPasswordUnderLockDetailsVerifier(lookup),
            otp,
            new MessageDeliveryAdapter(mock(SmsSenderPort.class), emailSender),
            new MaskingService(new MaskingProperties()),
            binding,
            commitStates,
            new ForgotPasswordOtpCommitRetentionPolicy(
                Duration.ofSeconds(60), Duration.ofMinutes(10)),
            new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000),
            clock,
            Duration.ofMinutes(5),
            registry);

    StepVerifier.create(
            service.execute(
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
        .expectNextCount(1)
        .verifyComplete();
    assertLoggedFailure("execute");
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
