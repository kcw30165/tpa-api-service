package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelProtectedExecutionResult.FailureBoundary;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.cancel.UsernameRecoveryCancelExecutionBridge;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Parallel Username Recovery CANCEL integration over the repository-wide shared executor. */
public final class IdempotentCancelUsernameRecoveryService
    implements CancelUsernameRecoveryUseCase {
  private static final int HTTP_OK = 200;
  private final IdempotencyExecutor executor;
  private final UsernameRecoveryCancelProtectedOperation operation;
  private final UsernameRecoveryCancelExecutionBridge bridge;
  private final UsernameRecoveryIdempotencyIntegrationPolicy policy;
  private final Clock clock;
  private final Duration ttl;

  public IdempotentCancelUsernameRecoveryService(
      IdempotencyExecutor executor,
      UsernameRecoveryCancelProtectedOperation operation,
      UsernameRecoveryCancelExecutionBridge bridge,
      UsernameRecoveryIdempotencyIntegrationPolicy policy,
      Clock clock,
      Duration ttl) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.bridge = Objects.requireNonNull(bridge);
    this.policy = Objects.requireNonNull(policy);
    this.clock = Objects.requireNonNull(clock);
    this.ttl = Objects.requireNonNull(ttl);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("CANCEL idempotency TTL must be positive");
    }
  }

  @Override
  public Mono<UsernameRecoveryCancelResult> cancel(
      UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer) {
    if (command == null || command.idempotencyKey().length() < 8) {
      return Mono.error(UsernameRecoveryCancelException.validationFailed());
    }
    String scope = command.authFlowSessionId().value();
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                UsernameRecoveryIdempotencyAction.CANCEL, scope, command.idempotencyKey()),
            policy.cancel(List.of(scope)),
            ttl,
            () -> executeAcquired(command, rawCsrfToken, origin, referer),
            this::classify,
            bridge::capture,
            bridge::hydrate);
    return executor
        .execute(execution)
        .flatMap(result -> route(result.lifecycleOutcome(), result.terminalResponse()))
        .onErrorMap(this::mapFailure);
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer) {
    return operation
        .execute(command, rawCsrfToken, origin, referer, clock.instant())
        .flatMap(
            result -> {
              if (result.completed()) {
                return Mono.just(bridge.toTransientResponse(result.result(), HTTP_OK));
              }
              return Mono.error(
                  new ProtectedCancelFailure(result.failureBoundary(), result.failure()));
            });
  }

  private IdempotencyFailureClassification classify(Throwable error) {
    if (error instanceof ProtectedCancelFailure failure
        && failure.boundary == FailureBoundary.PROVEN_PRE_DISPATCH) {
      return policy.provenPreDispatch().classify(error);
    }
    return policy.possibleSideEffect().classify(error);
  }

  private Mono<UsernameRecoveryCancelResult> route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> Mono.just(bridge.fromTransientResponse(response));
      case DUPLICATE_IN_PROGRESS ->
          Mono.error(UsernameRecoveryCancelException.idempotencyInProgress());
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          Mono.error(UsernameRecoveryCancelException.keyReused());
      case FAILED_EXISTING -> Mono.error(UsernameRecoveryCancelException.systemFailure());
      case UNKNOWN_OUTCOME_EXISTING ->
          Mono.error(UsernameRecoveryCancelException.unknownOutcomeExisting());
    };
  }

  private Throwable mapFailure(Throwable error) {
    if (error instanceof UsernameRecoveryCancelException) return error;
    if (error instanceof ProtectedCancelFailure failure
        && failure.getCause() instanceof UsernameRecoveryCancelException cancelException) {
      return cancelException;
    }
    return UsernameRecoveryCancelException.systemFailure(error);
  }

  private static final class ProtectedCancelFailure extends RuntimeException {
    private final FailureBoundary boundary;

    private ProtectedCancelFailure(FailureBoundary boundary, Throwable cause) {
      super("Username Recovery CANCEL protected execution failed", Objects.requireNonNull(cause));
      this.boundary = Objects.requireNonNull(boundary);
      if (boundary == FailureBoundary.NONE) {
        throw new IllegalArgumentException("protected failure requires a failure boundary");
      }
    }
  }
}
