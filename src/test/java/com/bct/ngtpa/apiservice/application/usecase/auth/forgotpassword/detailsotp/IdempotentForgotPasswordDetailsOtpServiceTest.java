package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.ForgotPasswordDetailsOtpExecutionBridge;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IdempotentForgotPasswordDetailsOtpServiceTest {
  @Test
  void validCommandUsesSessionScopeCanonicalFieldsTtlAndConservativeClassifier() {
    Fixture fixture = new Fixture();
    fixture.executor.outcome = IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.detailsAndOtp(any())).thenReturn(mock(IdempotencyFingerprint.class));

    assertThat(fixture.service.process(command()).block().status())
        .isEqualTo(ForgotPasswordDetailsOtpResult.Status.IN_PROGRESS);
    verify(fixture.policy)
        .operationId(
            ForgotPasswordIdempotencyAction.DETAILS_AND_OTP,
            "afs_test_123",
            "ac2e9b54-836d-4729-8957-d89b1d8ae015");
    verify(fixture.policy).detailsAndOtp(java.util.List.of("04/07/1978", "member@example.com", ""));
    assertThat(fixture.executor.command.ttl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(fixture.executor.command.failureClassifier().classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verify(fixture.operation, never()).execute(any());
  }

  @Test
  void invalidDetailsReturnBeforeReservation() {
    Fixture fixture = new Fixture();
    StepVerifier.create(
            fixture.service.process(
                new ForgotPasswordDetailsOtpCommand(
                    new AuthFlowSessionId("afs_test_123"),
                    "csrf",
                    "origin",
                    "referer",
                    "req",
                    "en",
                    "ac2e9b54-836d-4729-8957-d89b1d8ae015",
                    "bad",
                    null,
                    null)))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED))
        .verifyComplete();
    assertThat(fixture.executor.command).isNull();
  }

  @Test
  void acquiredExecutesOnceAndCarriesProtectedReferenceToCapture() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.detailsAndOtp(any())).thenReturn(mock(IdempotencyFingerprint.class));
    var result = success();
    var reference = new ProtectedReplayReference("opaque-ref-123456789012");
    var transientResponse = new HydratedIdempotentResponse(200, "application/json", "{}", Map.of());
    when(fixture.operation.execute(command()))
        .thenReturn(
            Mono.just(new ForgotPasswordDetailsOtpProtectedExecutionResult(result, reference)));
    when(fixture.responses.toTransientResponse(result)).thenReturn(transientResponse);
    when(fixture.responses.fromTransientResponse(any())).thenReturn(result);

    StepVerifier.create(fixture.service.process(command())).expectNext(result).verifyComplete();
    verify(fixture.operation).execute(command());
    assertThat(fixture.executor.protectedResponse.protectedReplayReference()).isEqualTo(reference);
  }

  @Test
  void mapsEveryNonTerminalLifecycleWithoutProtectedExecution() {
    assertMapped(
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
        ForgotPasswordDetailsOtpResult.Status.IN_PROGRESS);
    assertMapped(
        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
        ForgotPasswordDetailsOtpResult.Status.KEY_REUSED);
    assertMapped(
        IdempotencyReservationOutcome.FAILED_EXISTING,
        ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
    assertMapped(
        IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
        ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR);
  }

  @Test
  void protectedSystemResultCrossesExecutorBoundaryAsFailure() {
    Fixture fixture = new Fixture();
    fixture.executor.invokeProtected = true;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.detailsAndOtp(any())).thenReturn(mock(IdempotencyFingerprint.class));
    when(fixture.operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordDetailsOtpProtectedExecutionResult(
                    ForgotPasswordDetailsOtpResult.system(), null)));
    StepVerifier.create(fixture.service.process(command()))
        .expectErrorMessage("Forgot Password DETAILS_AND_OTP protected operation failed")
        .verify();
    verify(fixture.responses, never()).toTransientResponse(any());
  }

  private static void assertMapped(
      IdempotencyReservationOutcome outcome, ForgotPasswordDetailsOtpResult.Status expected) {
    Fixture fixture = new Fixture();
    fixture.executor.outcome = outcome;
    when(fixture.policy.operationId(any(), any(), any()))
        .thenReturn(mock(IdempotencyOperationId.class));
    when(fixture.policy.detailsAndOtp(any())).thenReturn(mock(IdempotencyFingerprint.class));
    assertThat(fixture.service.process(command()).block().status()).isEqualTo(expected);
    verify(fixture.operation, never()).execute(any());
  }

  private static ForgotPasswordDetailsOtpCommand command() {
    return new ForgotPasswordDetailsOtpCommand(
        new AuthFlowSessionId("afs_test_123"),
        "csrf",
        "origin",
        "referer",
        "req",
        "en",
        "ac2e9b54-836d-4729-8957-d89b1d8ae015",
        "04/07/1978",
        "member@example.com",
        null);
  }

  private static ForgotPasswordDetailsOtpResult success() {
    return new ForgotPasswordDetailsOtpResult(
        ForgotPasswordDetailsOtpResult.Status.SUCCESS,
        "m***@example.com",
        300,
        60,
        java.util.List.of());
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private IdempotentExecutionCommand command;
    private IdempotencyReservationOutcome outcome =
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private boolean invokeProtected;
    private HydratedIdempotentResponse protectedResponse;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
      command = supplied;
      if (invokeProtected) {
        return supplied
            .protectedOperation()
            .get()
            .map(
                response -> {
                  protectedResponse = response;
                  return new IdempotentExecutionResult(
                      IdempotencyReservationOutcome.ACQUIRED, response);
                });
      }
      return Mono.just(new IdempotentExecutionResult(outcome, null));
    }
  }

  private static final class Fixture {
    private final CapturingExecutor executor = new CapturingExecutor();
    private final ForgotPasswordDetailsOtpProtectedOperation operation =
        mock(ForgotPasswordDetailsOtpProtectedOperation.class);
    private final ForgotPasswordDetailsOtpExecutionBridge responses =
        mock(ForgotPasswordDetailsOtpExecutionBridge.class);
    private final ForgotPasswordIdempotencyIntegrationPolicy policy =
        mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
    private final IdempotentForgotPasswordDetailsOtpService service =
        new IdempotentForgotPasswordDetailsOtpService(
            executor, operation, responses, policy, Duration.ofMinutes(5));
  }
}
