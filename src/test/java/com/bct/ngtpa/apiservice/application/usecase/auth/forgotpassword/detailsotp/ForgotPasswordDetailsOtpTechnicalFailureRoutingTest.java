package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.ApimForgotPasswordDetailsLookupAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.ForgotPasswordDetailsOtpExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.net.ConnectException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

class ForgotPasswordDetailsOtpTechnicalFailureRoutingTest {
  private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
  private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_test_123");
  private static final SessionLockHandle LOCK =
      new SessionLockHandle(true, "lock-key", "owner-token");
  private final Logger verifierLogger =
      (Logger) LoggerFactory.getLogger(ForgotPasswordUnderLockDetailsVerifier.class);
  private final ListAppender<ILoggingEvent> verifierAppender = new ListAppender<>();

  @BeforeEach
  void attachVerifierAppender() {
    verifierAppender.start();
    verifierLogger.addAppender(verifierAppender);
  }

  @AfterEach
  void detachVerifierAppender() {
    verifierLogger.detachAppender(verifierAppender);
  }

  @Test
  void recognizedDetailsMismatchRemainsBusinessRejectedWithoutOtpSideEffects() {
    ForgotPasswordDetailsLookupPort lookup = mock(ForgotPasswordDetailsLookupPort.class);
    when(lookup.verify(any())).thenReturn(Mono.just(ForgotPasswordDetailsLookupResult.notFound()));
    var dependencies = new Dependencies();

    var result =
        dependencies
            .operation(new ForgotPasswordUnderLockDetailsVerifier(lookup))
            .execute(command())
            .block()
            .result();

    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.BUSINESS_REJECTED);
    assertThat(verifierAppender.list)
        .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
    verify(dependencies.otp, never()).createChallenge(any());
    verify(dependencies.delivery, never()).deliver(any());
  }

  @Test
  void apimConnectionFailureRemainsSystemUnavailableWithoutOtpSideEffects() {
    assertUnavailable(new ConnectException("offline"));
  }

  @Test
  void apimTimeoutAndHttpUnavailableRemainSystemUnavailable() {
    assertUnavailable(new java.net.SocketTimeoutException("timeout"));
    assertUnavailable(
        new WebClientRequestException(
            new java.io.IOException("offline"),
            HttpMethod.POST,
            URI.create("https://apim.example/ws/NGTPA/v1/TRPSearchMember"),
            new HttpHeaders()));
    assertUnavailable(
        new ApimException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCodes.APIM_SERVICE_UNAVAILABLE,
            "synthetic outage"));
    assertUnavailable(
        new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_TIMEOUT, "synthetic timeout"));
  }

  private static void assertUnavailable(Throwable failure) {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.error(failure));
    var adapter = new ApimForgotPasswordDetailsLookupAdapter(executor);
    var dependencies = new Dependencies();

    var result =
        dependencies
            .operation(new ForgotPasswordUnderLockDetailsVerifier(adapter))
            .execute(command())
            .block()
            .result();

    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE);
    verify(executor).postEnvelope(any());
    verify(dependencies.otp, never()).createChallenge(any());
    verify(dependencies.delivery, never()).deliver(any());
  }

  @Test
  void malformedApimResponseFailureRemainsTechnicalWithoutOtpSideEffects() {
    assertTechnicalFailure(
        new ApimException(
            HttpStatus.BAD_GATEWAY,
            ErrorCodes.APIM_RESPONSE_INVALID,
            "synthetic invalid response"));
  }

  @Test
  void missingApimResponseRemainsTechnicalWithoutOtpSideEffects() {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenReturn((Mono) Mono.just(new ApimResponseEnvelope<>(null)));
    var dependencies = new Dependencies();

    var result =
        dependencies
            .operation(
                new ForgotPasswordUnderLockDetailsVerifier(
                    new ApimForgotPasswordDetailsLookupAdapter(executor)))
            .execute(command())
            .block()
            .result();

    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
    verify(dependencies.otp, never()).createChallenge(any());
    verify(dependencies.delivery, never()).deliver(any());
  }

  @Test
  void profileResolutionFailureRemainsTechnicalWithoutOtpSideEffects() {
    assertTechnicalFailure(new ApimProfileResolutionException("synthetic profile failure"));
  }

  @Test
  void sessionReadExceptionIsLocalTechnicalFailureWithFixedStage() {
    var dependencies = new Dependencies();
    when(dependencies.sessions.loadSnapshot(SESSION_ID))
        .thenReturn(Mono.error(new IllegalStateException("SESSION-SECRET")));

    var execution =
        dependencies
            .operation(
                new ForgotPasswordUnderLockDetailsVerifier(
                    mock(ForgotPasswordDetailsLookupPort.class)))
            .execute(command())
            .block();

    assertThat(execution.result().status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
    assertThat(execution.failureOrigin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(execution.failureStage()).isEqualTo("session-read");
    verify(dependencies.locks).release(LOCK);
    verify(dependencies.otp, never()).createChallenge(any());
    verify(dependencies.delivery, never()).deliver(any());
  }

  @Test
  void commitStatePersistenceFailureKeepsItsStageAfterOtpDelivery() {
    var dependencies = new Dependencies();
    var challenge =
        new com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult(
            "otp-id", "123456", NOW.plusSeconds(300), 300);
    when(dependencies.otp.createChallenge(any())).thenReturn(Mono.just(challenge));
    when(dependencies.registry.resolve(any(), any(), any()))
        .thenReturn(
            new com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition(
                com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType
                    .FORGOT_PASSWORD_OTP,
                MessageDeliveryChannel.EMAIL,
                "en",
                "Subject",
                "Body {code}",
                java.util.Set.of("code"),
                "minutes"));
    when(dependencies.delivery.deliver(any()))
        .thenReturn(
            Mono.just(
                new MessageDeliveryResult(
                    MessageDeliveryStatus.ACCEPTED,
                    MessageDeliveryChannel.EMAIL,
                    null,
                    null,
                    null)));
    when(dependencies.binding.apply(any()))
        .thenReturn(
            Mono.just(
                new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                    .ForgotPasswordOtpBindingResult(
                    com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                        .ForgotPasswordOtpBindingResult.Status.BOUND)));
    when(dependencies.commitStates.saveIfAbsent(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("persistence failure")));
    var lookup = mock(ForgotPasswordDetailsLookupPort.class);
    when(lookup.verify(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordDetailsLookupResult(
                    com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details
                        .ForgotPasswordDetailsLookupStatus.MATCHED,
                    "trust-code",
                    "scheme-name",
                    "scheme-name-chi")));

    var execution =
        dependencies
            .operation(new ForgotPasswordUnderLockDetailsVerifier(lookup))
            .execute(command())
            .block();

    assertThat(execution.result().status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
    assertThat(execution.failureOrigin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(execution.failureStage()).isEqualTo("commit-state-persistence");
    verify(dependencies.otp).createChallenge(any());
    verify(dependencies.delivery).deliver(any());
    verify(dependencies.commitStates).saveIfAbsent(any(), any(), any());
    verify(dependencies.commitStates, never()).load(any());
    verify(dependencies.locks).release(LOCK);
  }

  @Test
  void genericDownstreamTechnicalObserverClassificationNeverOwnsTerminalError() {
    var lookup = mock(ForgotPasswordDetailsLookupPort.class);
    when(lookup.verify(any())).thenReturn(Mono.error(new IllegalStateException("unknown")));
    when(lookup.classifyFailure(any())).thenReturn(FailureOrigin.DOWNSTREAM_TECHNICAL);
    var dependencies = new Dependencies();

    var execution =
        dependencies
            .operation(new ForgotPasswordUnderLockDetailsVerifier(lookup))
            .execute(command())
            .block();

    assertThat(execution.failureOrigin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(execution.failureStage()).isEqualTo("details-lookup");
  }

  @Test
  void failedAndUncertainDeliveryAreLocalTechnicalFailuresWithoutResubmission() {
    assertDeliveryFailure(MessageDeliveryStatus.FAILED);
    assertDeliveryFailure(MessageDeliveryStatus.UNKNOWN_OUTCOME);
  }

  private static void assertDeliveryFailure(MessageDeliveryStatus deliveryStatus) {
    var dependencies = new Dependencies();
    var challenge =
        new com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult(
            "otp-id", "123456", NOW.plusSeconds(300), 300);
    when(dependencies.otp.createChallenge(any())).thenReturn(Mono.just(challenge));
    when(dependencies.registry.resolve(any(), any(), any()))
        .thenReturn(
            new com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition(
                com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType
                    .FORGOT_PASSWORD_OTP,
                MessageDeliveryChannel.EMAIL,
                "en",
                "Subject",
                "Body {code}",
                java.util.Set.of("code"),
                "minutes"));
    when(dependencies.delivery.deliver(any()))
        .thenReturn(
            Mono.just(
                new MessageDeliveryResult(
                    deliveryStatus, MessageDeliveryChannel.EMAIL, null, null, null)));
    when(dependencies.binding.apply(any()))
        .thenReturn(
            Mono.just(
                new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                    .ForgotPasswordOtpBindingResult(
                    com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp
                        .ForgotPasswordOtpBindingResult.Status.BOUND)));
    ForgotPasswordDetailsLookupPort lookup = mock(ForgotPasswordDetailsLookupPort.class);
    when(lookup.verify(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordDetailsLookupResult(
                    com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details
                        .ForgotPasswordDetailsLookupStatus.MATCHED,
                    "trust-code",
                    "scheme-name",
                    "scheme-name-chi")));

    var execution =
        dependencies
            .operation(new ForgotPasswordUnderLockDetailsVerifier(lookup))
            .execute(command())
            .block();

    assertThat(execution.result().status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
    assertThat(execution.failureOrigin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(execution.failureStage()).isEqualTo("message-delivery");
    verify(dependencies.otp).createChallenge(any());
    verify(dependencies.delivery).deliver(any());
    verify(dependencies.otp).createChallenge(any());
  }

  @Test
  void otherApimHttpFailuresKeepTheExistingSystemErrorClassification() {
    assertTechnicalFailure(
        new ApimException(
            HttpStatus.BAD_GATEWAY,
            ErrorCodes.APIM_UPSTREAM_FAILURE,
            "synthetic upstream failure"));
  }

  @Test
  void sameKeyReplayDoesNotRepeatLookupOrOtpSideEffects() {
    ApimExchangeExecutor apimExecutor = mock(ApimExchangeExecutor.class);
    when(apimExecutor.postEnvelope(any()))
        .thenReturn((Mono) Mono.error(new ConnectException("offline")));
    var dependencies = new Dependencies();
    var operation =
        dependencies.operation(
            new ForgotPasswordUnderLockDetailsVerifier(
                new ApimForgotPasswordDetailsLookupAdapter(apimExecutor)));
    var idempotencyExecutor = new CapturingIdempotencyExecutor();
    var responses = mock(ForgotPasswordDetailsOtpExecutionBridge.class);
    var policy = mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.detailsAndOtp(any())).thenReturn(mock(IdempotencyFingerprint.class));
    var unavailable =
        new ForgotPasswordDetailsOtpResult(
            ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE, null, 0, 0, List.of(), false);
    var transientResponse = new HydratedIdempotentResponse(503, "application/json", "{}", Map.of());
    when(responses.toTransientResponse(any())).thenReturn(transientResponse);
    when(responses.fromTransientResponse(transientResponse)).thenReturn(unavailable);
    var failureEvents = mock(FailureEventPort.class);
    var service =
        new IdempotentForgotPasswordDetailsOtpService(
            idempotencyExecutor,
            operation,
            responses,
            policy,
            Duration.ofMinutes(5),
            failureEvents);

    var first = service.process(command()).block();
    var replay = service.process(command()).block();

    assertThat(first.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_UNAVAILABLE);
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(first.incidentPublished()).isTrue();
    assertThat(replay.incidentPublished()).isFalse();
    verify(failureEvents).publish(any());
    assertThat(idempotencyExecutor.protectedExecutions).isEqualTo(1);
    verify(apimExecutor).postEnvelope(any());
    verify(dependencies.otp, never()).createChallenge(any());
    verify(dependencies.delivery, never()).deliver(any());
  }

  private static void assertTechnicalFailure(Throwable failure) {
    ApimExchangeExecutor executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any())).thenReturn((Mono) Mono.error(failure));
    var adapter = new ApimForgotPasswordDetailsLookupAdapter(executor);
    var dependencies = new Dependencies();

    var result =
        dependencies
            .operation(new ForgotPasswordUnderLockDetailsVerifier(adapter))
            .execute(command())
            .block()
            .result();

    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
    verify(dependencies.otp, never()).createChallenge(any());
    verify(dependencies.delivery, never()).deliver(any());
  }

  private static ForgotPasswordDetailsOtpCommand command() {
    return new ForgotPasswordDetailsOtpCommand(
        SESSION_ID,
        "csrf",
        "https://portal.example",
        "https://portal.example/forgot-password",
        "req-123",
        "en",
        "idem-123",
        "04/07/1978",
        "member@example.com",
        null);
  }

  private static final class Dependencies {
    private final AuthFlowSessionMutationLockPort locks =
        mock(AuthFlowSessionMutationLockPort.class);
    private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
    private final ForgotPasswordAuthFlowContextProtectorPort protector =
        mock(ForgotPasswordAuthFlowContextProtectorPort.class);
    private final OtpChallengePort otp = mock(OtpChallengePort.class);
    private final MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
    private final ForgotPasswordOtpSessionBindingPort binding =
        mock(ForgotPasswordOtpSessionBindingPort.class);
    private final ForgotPasswordOtpCommitStatePort commitStates =
        mock(ForgotPasswordOtpCommitStatePort.class);
    private final NoticeTemplateRegistryPort registry = mock(NoticeTemplateRegistryPort.class);

    private Dependencies() {
      when(locks.acquire(any(), any())).thenReturn(Mono.just(LOCK));
      when(locks.release(LOCK)).thenReturn(Mono.just(true));
      var session =
          new AuthFlowSession(
              SESSION_ID,
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
              NOW.minusSeconds(30),
              NOW.minusSeconds(5),
              NOW.plusSeconds(300));
      when(sessions.loadSnapshot(SESSION_ID))
          .thenReturn(
              Mono.just(
                  Optional.of(
                      new AuthFlowSessionSnapshot(
                          session, new SessionRevision("0123456789abcdef0123456789abcdef")))));
      when(protector.unprotect(any()))
          .thenReturn(
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
                      SESSION_ID.value(),
                      "reset-subject-id",
                      NOW,
                      NOW.plusSeconds(600),
                      null),
                  null,
                  null,
                  null,
                  "v1",
                  ForgotPasswordProtectedPhase.IDENTITY_VERIFIED));
    }

    private ForgotPasswordDetailsOtpProtectedOperation operation(
        ForgotPasswordUnderLockDetailsVerifier verifier) {
      return new ForgotPasswordDetailsOtpProtectedOperation(
          new SecureForgotPasswordOtpCommitRefGenerator(),
          locks,
          sessions,
          protector,
          verifier,
          otp,
          delivery,
          new MaskingService(new MaskingProperties()),
          binding,
          commitStates,
          new ForgotPasswordOtpCommitRetentionPolicy(
              Duration.ofSeconds(60), Duration.ofMinutes(10)),
          new ForgotPasswordDetailsOtpLeasePolicy(60, 10, 10, 3, 10000, 100, 5000, 1000),
          Clock.fixed(NOW, ZoneOffset.UTC),
          Duration.ofMinutes(5),
          registry);
    }
  }

  private static final class CapturingIdempotencyExecutor implements IdempotencyExecutor {
    private HydratedIdempotentResponse storedResponse;
    private int protectedExecutions;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      if (storedResponse != null) {
        return Mono.just(
            new IdempotentExecutionResult(
                IdempotencyReservationOutcome.COMPLETED_REPLAY, storedResponse));
      }
      protectedExecutions++;
      return command
          .protectedOperation()
          .get()
          .map(
              response -> {
                storedResponse = response;
                return new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.ACQUIRED, response);
              });
    }

    @Override
    public Mono<Optional<IdempotentExecutionResult>> replayCompleted(
        IdempotencyOperationId operationId,
        IdempotencyFingerprint fingerprint,
        com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayResolver replayResolver) {
      return Mono.error(new AssertionError("Unexpected replay-only lookup"));
    }
  }
}
