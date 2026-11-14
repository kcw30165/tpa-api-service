package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend.ForgotPasswordOtpResendExecutionBridge;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link IdempotentResendForgotPasswordOtpService}. Mirrors the
 * proven ArgumentCaptor + direct-supplier-invocation technique from
 * IdempotentCompleteForgotPasswordServiceTest (round 58) to exercise executeAcquired.
 */
class IdempotentResendForgotPasswordOtpServiceTest {

  private final IdempotencyExecutor executor = mock(IdempotencyExecutor.class);
  private final ForgotPasswordOtpResendProtectedOperation operation =
      mock(ForgotPasswordOtpResendProtectedOperation.class);
  private final ForgotPasswordOtpResendExecutionBridge responses =
      mock(ForgotPasswordOtpResendExecutionBridge.class);
  private final ForgotPasswordIdempotencyIntegrationPolicy policy =
      mock(ForgotPasswordIdempotencyIntegrationPolicy.class);
  private final FailureEventPort failureEvents = mock(FailureEventPort.class);
  private final Duration ttl = Duration.ofMinutes(5);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-idem-resend-1");

  private IdempotentResendForgotPasswordOtpService newService() {
    return new IdempotentResendForgotPasswordOtpService(
        executor, operation, responses, policy, ttl, failureEvents);
  }

  @Test
  void deliveryFailedPublishesCauseFreeLocalSystemIncident() throws Exception {
    stubPolicyIdentity();
    ResendForgotPasswordOtpResult failed =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.DELIVERY_FAILED, null, null, null, null, null);
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendProtectedExecutionResult(
                    failed, null, java.util.List.of(ForgotPasswordOtpResendIncident.MESSAGE_DELIVERY))));
    when(responses.toTransientResponse(failed)).thenReturn(response);
    when(responses.fromTransientResponse(response)).thenReturn(failed);
    when(executor.execute(any()))
        .thenAnswer(
            invocation -> {
              IdempotentExecutionCommand execution = invocation.getArgument(0);
              return execution
                  .protectedOperation()
                  .get()
                  .map(
                      captured ->
                          new IdempotentExecutionResult(
                              IdempotencyReservationOutcome.ACQUIRED, captured));
            });

    IdempotentResendForgotPasswordOtpService service = serviceWithFailureEvents();
    StepVerifier.create(service.resend(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(failed.status());
              assertThat(result.publicationReceipt()).isNotNull();
              assertThat(result.publicationReceipt().terminalFailure()).isTrue();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    org.mockito.Mockito.verify(failureEvents).publish(event.capture());
    assertThat(event.getValue().event()).isEqualTo("forgot-password.resend.failed");
    assertThat(event.getValue().capability()).isEqualTo("FORGOT_PASSWORD");
    assertThat(event.getValue().operation()).isEqualTo("OTP_RESEND");
    assertThat(event.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_RESEND_TECHNICAL_FAILURE");
    assertThat(event.getValue().safeDimensions()).containsEntry("stage", "message-delivery");
    assertThat(event.getValue().httpStatus()).isEqualTo(503);
    assertThat(event.getValue().retryable()).isFalse();
    org.mockito.Mockito.verify(failureEvents, org.mockito.Mockito.never())
        .publish(any(FailureEvent.class), any(Throwable.class));
  }

  @Test
  void unknownDeliveryOutcomePublishesDistinctIncidentWithoutCause() {
    stubPolicyIdentity();
    ResendForgotPasswordOtpResult unknown =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendProtectedExecutionResult(
                    unknown,
                    null,
                    java.util.List.of(ForgotPasswordOtpResendIncident.UNKNOWN_DELIVERY))));
    executeProtectedAsAcquired();

    StepVerifier.create(newService().resend(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(result.publicationReceipt().terminalFailure()).isTrue();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    org.mockito.Mockito.verify(failureEvents).publish(event.capture());
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_RESEND_UNKNOWN_DELIVERY_OUTCOME");
    assertThat(event.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(event.getValue().httpStatus()).isEqualTo(503);
    org.mockito.Mockito.verify(failureEvents, org.mockito.Mockito.never())
        .publish(any(FailureEvent.class), any(Throwable.class));
  }

  private IdempotentResendForgotPasswordOtpService serviceWithFailureEvents()
      throws ReflectiveOperationException {
    var constructor =
        java.util.Arrays.stream(IdempotentResendForgotPasswordOtpService.class.getConstructors())
            .filter(
                candidate ->
                    candidate.getParameterCount() == 6
                        && candidate.getParameterTypes()[5] == FailureEventPort.class)
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("FailureEventPort constructor has not been added"));
    return (IdempotentResendForgotPasswordOtpService)
        constructor.newInstance(executor, operation, responses, policy, ttl, failureEvents);
  }

  @Test
  void nonPositiveTtlThrows() {
    assertThatThrownBy(
            () ->
                new IdempotentResendForgotPasswordOtpService(
                    executor, operation, responses, policy, Duration.ZERO, failureEvents))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ResendForgotPasswordOtpCommand command() {
    return new ResendForgotPasswordOtpCommand(
        sid,
        "csrf-token",
        "https://portal.bcthk.com",
        "https://portal.bcthk.com/resend",
        "req-1",
        "en",
        "idem-1");
  }

  @Test
  void nullCommandThrows() {
    assertThatThrownBy(() -> newService().resend(null)).isInstanceOf(NullPointerException.class);
  }

  private void stubPolicyIdentity() {
    when(policy.operationId(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(policy.otpResend())
        .thenReturn(
            mock(com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint.class));
  }

  /** Captures the command passed to executor.execute(...) and returns it. */
  private IdempotentExecutionCommand captureExecutionCommand() {
    stubPolicyIdentity();
    ArgumentCaptor<IdempotentExecutionCommand> captor =
        ArgumentCaptor.forClass(IdempotentExecutionCommand.class);
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(executor.execute(captor.capture()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.ACQUIRED, response)));
    when(responses.fromTransientResponse(response))
        .thenReturn(
            new ResendForgotPasswordOtpResult(
                ResendForgotPasswordOtpStatus.SECURITY_REJECTED, null, null, null, null, null));
    newService().resend(command()).block();
    return captor.getValue();
  }

  private void executeProtectedAsAcquired() {
    when(executor.execute(any()))
        .thenAnswer(
            invocation -> {
              IdempotentExecutionCommand execution = invocation.getArgument(0);
              return execution
                  .protectedOperation()
                  .get()
                  .map(
                      response ->
                          new IdempotentExecutionResult(
                              IdempotencyReservationOutcome.ACQUIRED, response));
            });
  }

  // ----- direct protectedOperation supplier tests (executeAcquired) -----------

  @Test
  void protectedOperationSystemFailedErrors() {
    ResendForgotPasswordOtpResult failed =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(Mono.just(new ForgotPasswordOtpResendProtectedExecutionResult(failed, null)));
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get())
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void protectedOperationSuccessWithReplayReferenceAttaches() {
    ResendForgotPasswordOtpResult resent =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null);
    ProtectedReplayReference reference = new ProtectedReplayReference("replay-ref-1234567890abcd");
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(new ForgotPasswordOtpResendProtectedExecutionResult(resent, reference)));
    HydratedIdempotentResponse baseResponse = mock(HydratedIdempotentResponse.class);
    HydratedIdempotentResponse withRef = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(resent)).thenReturn(baseResponse);
    when(baseResponse.withProtectedReplayReference(reference)).thenReturn(withRef);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get()).expectNext(withRef).verifyComplete();
  }

  @Test
  void protectedOperationSuccessWithoutReplayReferenceReturnsBase() {
    ResendForgotPasswordOtpResult securityRejected =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.SECURITY_REJECTED, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(new ForgotPasswordOtpResendProtectedExecutionResult(securityRejected, null)));
    HydratedIdempotentResponse baseResponse = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(securityRejected)).thenReturn(baseResponse);
    IdempotentExecutionCommand cmd = captureExecutionCommand();
    StepVerifier.create(cmd.protectedOperation().get()).expectNext(baseResponse).verifyComplete();
    org.mockito.Mockito.verifyNoInteractions(failureEvents);
  }

  @Test
  void malformedCounterIncidentCarriesNonTerminalReceiptOnAcquiredSuccess() {
    stubPolicyIdentity();
    ResendForgotPasswordOtpResult resent =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null);
    ProtectedReplayReference reference =
        new ProtectedReplayReference("replay-ref-1234567890abcd");
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendProtectedExecutionResult(
                    resent,
                    reference,
                    java.util.List.of(
                        ForgotPasswordOtpResendIncident.MALFORMED_RESEND_COUNT))));
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    HydratedIdempotentResponse responseWithReference = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(resent)).thenReturn(response);
    when(response.withProtectedReplayReference(reference)).thenReturn(responseWithReference);
    when(responses.fromTransientResponse(responseWithReference)).thenReturn(resent);
    executeProtectedAsAcquired();

    StepVerifier.create(newService().resend(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.RESENT);
              assertThat(result.publicationReceipt()).isNotNull();
              assertThat(result.publicationReceipt().terminalFailure()).isFalse();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    org.mockito.Mockito.verify(failureEvents).publish(event.capture());
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_RESEND_COUNTER_MALFORMED");
    assertThat(event.getValue().safeDimensions()).containsEntry("stage", "resend-count");
    assertThat(event.getValue().httpStatus()).isEqualTo(200);
  }

  @Test
  void technicalIncidentRetainsMappedClientErrorStatus() {
    stubPolicyIdentity();
    ResendForgotPasswordOtpResult invalidFlow =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendProtectedExecutionResult(
                    invalidFlow,
                    null,
                    java.util.List.of(ForgotPasswordOtpResendIncident.CONTEXT_UNPROTECT))));
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(responses.toTransientResponse(invalidFlow)).thenReturn(response);
    when(responses.fromTransientResponse(response)).thenReturn(invalidFlow);
    executeProtectedAsAcquired();

    StepVerifier.create(newService().resend(command()))
        .assertNext(result -> assertThat(result.status()).isEqualTo(invalidFlow.status()))
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    org.mockito.Mockito.verify(failureEvents).publish(event.capture());
    assertThat(event.getValue().httpStatus()).isEqualTo(400);
  }

  @Test
  void completedReplayDoesNotPublishOrInheritReceipt() {
    stubPolicyIdentity();
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.COMPLETED_REPLAY, response)));
    ResendForgotPasswordOtpResult replay =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.DELIVERY_FAILED, null, null, null, null, null);
    when(responses.fromTransientResponse(response)).thenReturn(replay);

    StepVerifier.create(newService().resend(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.DELIVERY_FAILED);
              assertThat(result.publicationReceipt()).isNull();
            })
        .verifyComplete();

    org.mockito.Mockito.verifyNoInteractions(failureEvents, operation);
  }

  @Test
  void independentExecutorFailurePublishesItsOwnTerminalIncident() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(Mono.error(new IllegalStateException("store-value-must-not-leak")));

    StepVerifier.create(newService().resend(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(result.publicationReceipt()).isNotNull();
              assertThat(result.publicationReceipt().terminalFailure()).isTrue();
            })
        .verifyComplete();

    ArgumentCaptor<FailureEvent> event = ArgumentCaptor.forClass(FailureEvent.class);
    org.mockito.Mockito.verify(failureEvents).publish(event.capture());
    assertThat(event.getValue().internalCode()).isEqualTo("OTP_RESEND_IDEMPOTENCY_FAILURE");
    assertThat(event.getValue().safeDimensions()).containsEntry("stage", "idempotency");
    assertThat(event.getValue().requestId()).isEqualTo("req-1");
    assertThat(event.getValue().toString()).doesNotContain("store-value-must-not-leak");
    org.mockito.Mockito.verify(failureEvents, org.mockito.Mockito.never())
        .publish(any(FailureEvent.class), any(Throwable.class));
  }

  @Test
  void independentExecutorFailureIsPublishedAfterProtectedIncident() {
    stubPolicyIdentity();
    ResendForgotPasswordOtpResult failed =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendProtectedExecutionResult(
                    failed,
                    null,
                    java.util.List.of(ForgotPasswordOtpResendIncident.FOUNDATION))));
    when(executor.execute(any()))
        .thenAnswer(
            invocation -> {
              IdempotentExecutionCommand execution = invocation.getArgument(0);
              return execution
                  .protectedOperation()
                  .get()
                  .map(
                      response ->
                          new IdempotentExecutionResult(
                              IdempotencyReservationOutcome.ACQUIRED, response))
                  .onErrorResume(
                      ignored -> Mono.error(new IllegalStateException("unknown-persist-secret")));
            });

    StepVerifier.create(newService().resend(command()))
        .assertNext(result -> assertThat(result.publicationReceipt().terminalFailure()).isTrue())
        .verifyComplete();

    ArgumentCaptor<FailureEvent> events = ArgumentCaptor.forClass(FailureEvent.class);
    org.mockito.Mockito.verify(failureEvents, org.mockito.Mockito.times(2)).publish(events.capture());
    assertThat(events.getAllValues())
        .extracting(FailureEvent::internalCode)
        .containsExactly("OTP_RESEND_TECHNICAL_FAILURE", "OTP_RESEND_IDEMPOTENCY_FAILURE");
  }

  // ----- route() outcome-switch branches (via canned executor results) --------------

  @Test
  void acquiredDelegatesToResponsesFromTransientResponse() {
    stubPolicyIdentity();
    HydratedIdempotentResponse response = mock(HydratedIdempotentResponse.class);
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, response)));
    ResendForgotPasswordOtpResult expected =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.RESENT, "j***@example.com", 300, 60, 4, null);
    when(responses.fromTransientResponse(response)).thenReturn(expected);
    StepVerifier.create(newService().resend(command())).expectNext(expected).verifyComplete();
  }

  @Test
  void duplicateInProgressYieldsSystemFailed() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
    org.mockito.Mockito.verifyNoInteractions(failureEvents, operation);
  }

  @Test
  void keyReusedYieldsIdempotencyKeyReused() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED))
        .verifyComplete();
    org.mockito.Mockito.verifyNoInteractions(failureEvents, operation);
  }

  @Test
  void failedExistingYieldsPreviousFailed() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.FAILED_EXISTING, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.PREVIOUS_FAILED))
        .verifyComplete();
    org.mockito.Mockito.verifyNoInteractions(failureEvents, operation);
  }

  @Test
  void unknownOutcomeExistingYieldsUnknownOutcome() {
    stubPolicyIdentity();
    when(executor.execute(any()))
        .thenReturn(
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, null)));
    StepVerifier.create(newService().resend(command()))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME);
              assertThat(r.publicationReceipt()).isNull();
            })
        .verifyComplete();
    org.mockito.Mockito.verifyNoInteractions(failureEvents, operation);
  }

  @Test
  void expectedLockContentionSystemResultDoesNotPublishOrMarkOwnership() {
    stubPolicyIdentity();
    ResendForgotPasswordOtpResult contention =
        new ResendForgotPasswordOtpResult(
            ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null);
    when(operation.execute(any()))
        .thenReturn(
            Mono.just(new ForgotPasswordOtpResendProtectedExecutionResult(contention, null)));
    when(executor.execute(any()))
        .thenAnswer(
            invocation -> {
              IdempotentExecutionCommand execution = invocation.getArgument(0);
              return execution
                  .protectedOperation()
                  .get()
                  .map(
                      response ->
                          new IdempotentExecutionResult(
                              IdempotencyReservationOutcome.ACQUIRED, response));
            });

    StepVerifier.create(newService().resend(command()))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED);
              assertThat(result.publicationReceipt()).isNull();
            })
        .verifyComplete();
    org.mockito.Mockito.verifyNoInteractions(failureEvents);
  }

  @Test
  void executorErrorYieldsSystemFailed() {
    stubPolicyIdentity();
    when(executor.execute(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().resend(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }
}
