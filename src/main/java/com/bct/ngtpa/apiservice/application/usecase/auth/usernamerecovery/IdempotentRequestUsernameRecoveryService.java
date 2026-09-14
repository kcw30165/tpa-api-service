package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.request.UsernameRecoveryRequestExecutionBridge;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Parallel Username Recovery REQUEST integration over the repository-wide shared executor. */
public final class IdempotentRequestUsernameRecoveryService
    implements RequestUsernameRecoveryUseCase {
  private static final int HTTP_OK = 200;
  private final IdempotencyExecutor executor;
  private final UsernameRecoveryRequestProtectedOperation protectedOperation;
  private final UsernameRecoveryRequestExecutionBridge bridge;
  private final UsernameRecoveryIdempotencyIntegrationPolicy policy;
  private final Clock clock;
  private final Duration ttl;

  public IdempotentRequestUsernameRecoveryService(
      IdempotencyExecutor executor,
      UsernameRecoveryRequestProtectedOperation protectedOperation,
      UsernameRecoveryRequestExecutionBridge bridge,
      UsernameRecoveryIdempotencyIntegrationPolicy policy,
      Clock clock,
      Duration ttl) {
    this.executor = Objects.requireNonNull(executor);
    this.protectedOperation = Objects.requireNonNull(protectedOperation);
    this.bridge = Objects.requireNonNull(bridge);
    this.policy = Objects.requireNonNull(policy);
    this.clock = Objects.requireNonNull(clock);
    this.ttl = Objects.requireNonNull(ttl);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("REQUEST idempotency TTL must be positive");
    }
  }

  @Override
  public Mono<UsernameRecoveryRequestResult> request(
      UsernameRecoveryRequestCommand command, String rawCsrfToken, String origin, String referer) {
    if (command == null) {
      return Mono.error(UsernameRecoveryRequestException.validationFailed());
    }
    if (command.idempotencyKey().length() < 8) {
      return Mono.error(UsernameRecoveryRequestException.validationFailed());
    }
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                UsernameRecoveryIdempotencyAction.REQUEST,
                command.authFlowSessionId().value(),
                command.idempotencyKey()),
            policy.request(
                List.of(command.authFlowSessionId().value(), command.schemeRef().value())),
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
      UsernameRecoveryRequestCommand command, String rawCsrfToken, String origin, String referer) {
    String correlationId = "op_" + UUID.randomUUID().toString().replace("-", "");
    return protectedOperation
        .execute(command, rawCsrfToken, origin, referer, correlationId, clock.instant())
        .flatMap(
            execution -> {
              if (execution.completed()) {
                return Mono.just(bridge.toTransientResponse(execution.result(), HTTP_OK));
              }
              return Mono.error(
                  new ProtectedRequestFailure(execution.failureBoundary(), execution.failure()));
            });
  }

  private IdempotencyFailureClassification classify(Throwable error) {
    if (error instanceof ProtectedRequestFailure failure
        && failure.boundary == FailureBoundary.PROVEN_PRE_DISPATCH) {
      return policy.provenPreDispatch().classify(error);
    }
    return policy.possibleSideEffect().classify(error);
  }

  private Mono<UsernameRecoveryRequestResult> route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> Mono.just(bridge.fromTransientResponse(response));
      case DUPLICATE_IN_PROGRESS ->
          Mono.error(UsernameRecoveryRequestException.idempotencyInProgress());
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          Mono.error(UsernameRecoveryRequestException.keyReused());
      case FAILED_EXISTING -> Mono.error(UsernameRecoveryRequestException.systemFailure());
      case UNKNOWN_OUTCOME_EXISTING ->
          Mono.error(UsernameRecoveryRequestException.unknownOutcomeExisting());
    };
  }

  private Throwable mapFailure(Throwable error) {
    if (error instanceof UsernameRecoveryRequestException) {
      return error;
    }
    if (error instanceof ProtectedRequestFailure failure
        && failure.getCause() instanceof UsernameRecoveryRequestException requestException) {
      return requestException;
    }
    return UsernameRecoveryRequestException.systemFailure(error);
  }

  private static final class ProtectedRequestFailure extends RuntimeException {
    private final FailureBoundary boundary;

    private ProtectedRequestFailure(FailureBoundary boundary, Throwable cause) {
      super("Username Recovery REQUEST protected execution failed", Objects.requireNonNull(cause));
      this.boundary = Objects.requireNonNull(boundary);
      if (boundary == FailureBoundary.NONE) {
        throw new IllegalArgumentException("protected failure requires a failure boundary");
      }
    }

    @Override
    public String toString() {
      return "ProtectedRequestFailure[boundary="
          + boundary
          + ", causeType="
          + getCause().getClass().getSimpleName()
          + "]";
    }
  }
}
