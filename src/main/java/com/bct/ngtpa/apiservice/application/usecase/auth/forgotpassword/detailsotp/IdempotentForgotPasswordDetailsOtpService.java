package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp.ForgotPasswordDetailsOtpExecutionBridge;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Thin DETAILS_AND_OTP integration over the repository-wide shared idempotency executor. */
public final class IdempotentForgotPasswordDetailsOtpService
    implements ProcessForgotPasswordDetailsOtpUseCase {
  private final IdempotencyExecutor executor;
  private final ForgotPasswordDetailsOtpProtectedOperation operation;
  private final ForgotPasswordDetailsOtpExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;
  private final ForgotPasswordDetailsValidator validator = new ForgotPasswordDetailsValidator();
  private final Duration ttl;

  public IdempotentForgotPasswordDetailsOtpService(
      IdempotencyExecutor executor,
      ForgotPasswordDetailsOtpProtectedOperation operation,
      ForgotPasswordDetailsOtpExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy,
      Duration ttl) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
    this.ttl = Objects.requireNonNull(ttl);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("DETAILS_AND_OTP idempotency TTL must be positive");
    }
  }

  @Override
  public Mono<ForgotPasswordDetailsOtpResult> process(ForgotPasswordDetailsOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var validation = validator.validate(command.dateOfBirth(), command.email(), command.mobile());
    if (!validation.valid()) {
      return Mono.just(new ForgotPasswordDetailsOtpResult(
          ForgotPasswordDetailsOtpResult.Status.VALIDATION_FAILED,
          null, 0, 0, validation.targets()));
    }
    var execution = new IdempotentExecutionCommand(
        policy.operationId(ForgotPasswordIdempotencyAction.DETAILS_AND_OTP,
            command.sessionId().value(), command.idempotencyKey()),
        policy.detailsAndOtp(List.of(command.dateOfBirth(),
            Objects.toString(command.email(), ""), Objects.toString(command.mobile(), ""))),
        ttl,
        () -> executeAcquired(command),
        IdempotencyFailureClassifiers.possibleSideEffect(),
        response -> responses.capture(response, response.protectedReplayReference()),
        responses::hydrate);
    return executor.execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      ForgotPasswordDetailsOtpCommand command) {
    return operation.execute(command).flatMap(execution -> {
      if (execution.result().status() == ForgotPasswordDetailsOtpResult.Status.SYSTEM_ERROR) {
        return Mono.error(new IllegalStateException(
            "Forgot Password DETAILS_AND_OTP protected operation failed"));
      }
      HydratedIdempotentResponse response = responses.toTransientResponse(execution.result());
      return Mono.just(execution.replayReference() == null
          ? response : response.withProtectedReplayReference(execution.replayReference()));
    });
  }

  private ForgotPasswordDetailsOtpResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS -> simple(ForgotPasswordDetailsOtpResult.Status.IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST -> simple(ForgotPasswordDetailsOtpResult.Status.KEY_REUSED);
      case FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING -> ForgotPasswordDetailsOtpResult.system();
    };
  }

  private ForgotPasswordDetailsOtpResult simple(ForgotPasswordDetailsOtpResult.Status status) {
    return new ForgotPasswordDetailsOtpResult(status, null, 0, 0, List.of());
  }
}
