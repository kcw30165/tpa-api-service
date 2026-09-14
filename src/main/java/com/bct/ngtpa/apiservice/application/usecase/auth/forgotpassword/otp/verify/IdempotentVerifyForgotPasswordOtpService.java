package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify.ForgotPasswordOtpVerifyExecutionBridge;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Thin OTP_VERIFY integration over the repository-wide shared idempotency executor. */
public final class IdempotentVerifyForgotPasswordOtpService
    implements VerifyForgotPasswordOtpUseCase {
  private final IdempotencyExecutor executor;
  private final ForgotPasswordOtpVerifyProtectedOperation operation;
  private final ForgotPasswordOtpVerifyExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;

  public IdempotentVerifyForgotPasswordOtpService(
      IdempotencyExecutor executor,
      ForgotPasswordOtpVerifyProtectedOperation operation,
      ForgotPasswordOtpVerifyExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
  }

  @Override
  public Mono<VerifyForgotPasswordOtpResult> verify(VerifyForgotPasswordOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                ForgotPasswordIdempotencyAction.OTP_VERIFY,
                command.sessionId().value(),
                command.idempotencyKey()),
            policy.otpVerify(),
            command.idempotencyTtl(),
            () -> executeAcquired(command),
            IdempotencyFailureClassifiers.possibleSideEffect(),
            responses::capture,
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()))
        .onErrorReturn(simple(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE));
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(VerifyForgotPasswordOtpCommand command) {
    return operation
        .execute(command)
        .flatMap(
            execution -> {
              VerifyForgotPasswordOtpResult result = execution.result();
              if (result.status() == VerifyForgotPasswordOtpStatus.SYSTEM_FAILED) {
                return Mono.error(
                    new IllegalStateException(
                        "Forgot Password OTP_VERIFY protected operation failed"));
              }
              return Mono.just(responses.toTransientResponse(result));
            });
  }

  private VerifyForgotPasswordOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS ->
          simple(VerifyForgotPasswordOtpStatus.DUPLICATE_IN_PROGRESS, WorkflowNextStep.WAIT);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          simple(VerifyForgotPasswordOtpStatus.IDEMPOTENCY_KEY_REUSED, WorkflowNextStep.NONE);
      case FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING ->
          simple(VerifyForgotPasswordOtpStatus.SYSTEM_FAILED, WorkflowNextStep.NONE);
    };
  }

  private VerifyForgotPasswordOtpResult simple(
      VerifyForgotPasswordOtpStatus status, WorkflowNextStep nextStep) {
    return new VerifyForgotPasswordOtpResult(status, nextStep, null);
  }
}
