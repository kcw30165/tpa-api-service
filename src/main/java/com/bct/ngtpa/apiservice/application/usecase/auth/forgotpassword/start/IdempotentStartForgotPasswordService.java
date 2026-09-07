package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassifiers;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartExecutionBridge;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Thin Forgot Password START integration over the repository-wide shared idempotency executor. */
public final class IdempotentStartForgotPasswordService implements StartForgotPasswordUseCase {
  static final String ANONYMOUS_START_SCOPE = "forgot-password-start-anonymous-v1";

  private final IdempotencyExecutor executor;
  private final ForgotPasswordStartProtectedOperation operation;
  private final ForgotPasswordStartExecutionBridge responses;
  private final ForgotPasswordIdempotencyIntegrationPolicy policy;

  public IdempotentStartForgotPasswordService(
      IdempotencyExecutor executor,
      ForgotPasswordStartProtectedOperation operation,
      ForgotPasswordStartExecutionBridge responses,
      ForgotPasswordIdempotencyIntegrationPolicy policy) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
  }

  @Override
  public Mono<StartForgotPasswordResult> start(StartForgotPasswordCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String normalizedUsername = operation.normalizeUsername(command.username());
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                ForgotPasswordIdempotencyAction.START,
                ANONYMOUS_START_SCOPE,
                command.idempotencyKey().value()),
            policy.start(normalizedUsername),
            command.idempotencyTtl(),
            () -> executeAcquired(command),
            IdempotencyFailureClassifiers.possibleSideEffect(),
            response -> responses.capture(response, command.idempotencyTtl()),
            responses::hydrate);
    return executor
        .execute(execution)
        .map(result -> route(result.lifecycleOutcome(), result.terminalResponse()));
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(StartForgotPasswordCommand command) {
    return operation
        .execute(command)
        .flatMap(
            result ->
                result.status() == StartForgotPasswordResultStatus.SYSTEM_FAILED
                    ? Mono.error(
                        new IllegalStateException(
                            "Forgot Password Start protected operation failed"))
                    : Mono.just(responses.toTransientResponse(result)));
  }

  private StartForgotPasswordResult route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response);
      case DUPLICATE_IN_PROGRESS -> simple(StartForgotPasswordResultStatus.DUPLICATE_IN_PROGRESS);
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          simple(StartForgotPasswordResultStatus.IDEMPOTENCY_KEY_REUSED);
      case FAILED_EXISTING -> simple(StartForgotPasswordResultStatus.FAILED_EXISTING);
      case UNKNOWN_OUTCOME_EXISTING -> simple(StartForgotPasswordResultStatus.UNKNOWN_OUTCOME);
    };
  }

  private StartForgotPasswordResult simple(StartForgotPasswordResultStatus status) {
    return new StartForgotPasswordResult(
        status, WorkflowNextStep.NONE, null, null, null, List.of(), null);
  }
}
