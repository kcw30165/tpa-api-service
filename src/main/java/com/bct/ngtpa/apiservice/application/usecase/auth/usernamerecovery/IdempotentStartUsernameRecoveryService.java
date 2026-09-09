package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Error;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Kind;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyIntegrationPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartExecutionBridge;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Parallel Username Recovery START integration over the repository-wide shared executor. */
public final class IdempotentStartUsernameRecoveryService implements StartUsernameRecoveryUseCase {
  static final String ANONYMOUS_START_SCOPE = "username-recovery-start-anonymous-v1";
  private static final int HTTP_OK = 200;

  private final IdempotencyExecutor executor;
  private final UsernameRecoveryStartProtectedOperation operation;
  private final UsernameRecoveryStartExecutionBridge responses;
  private final UsernameRecoveryIdempotencyIntegrationPolicy policy;
  private final UsernameRecoveryStartValidator validator;
  private final Duration ttl;

  public IdempotentStartUsernameRecoveryService(
      IdempotencyExecutor executor,
      UsernameRecoveryStartProtectedOperation operation,
      UsernameRecoveryStartExecutionBridge responses,
      UsernameRecoveryIdempotencyIntegrationPolicy policy,
      UsernameRecoveryStartValidator validator,
      Duration ttl) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
    this.responses = Objects.requireNonNull(responses);
    this.policy = Objects.requireNonNull(policy);
    this.validator = Objects.requireNonNull(validator);
    this.ttl = Objects.requireNonNull(ttl);
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("START idempotency TTL must be positive");
    }
  }

  @Override
  public Mono<UsernameRecoveryStartResult> start(UsernameRecoveryStartCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    UsernameRecoveryStartValidator.Validation validation = validator.validate(command);
    if (!validation.valid()) {
      return Mono.error(UsernameRecoveryStartException.validationFailed(validation.errors()));
    }
    if (command.idempotencyKey().length() < 8) {
      return Mono.error(
          UsernameRecoveryStartException.validationFailed(
              List.of(new Error("idempotencyKey", Kind.IDEMPOTENCY_KEY_INVALID))));
    }
    var execution =
        new IdempotentExecutionCommand(
            policy.operationId(
                UsernameRecoveryIdempotencyAction.START,
                ANONYMOUS_START_SCOPE,
                command.idempotencyKey()),
            policy.start(canonicalFields(command, validation)),
            ttl,
            () -> executeAcquired(command, validation),
            policy.possibleSideEffect(),
            response -> responses.capture(response, ttl),
            responses::hydrate);
    return executor
        .execute(execution)
        .flatMap(result -> route(result.lifecycleOutcome(), result.terminalResponse()))
        .onErrorMap(
            error ->
                error instanceof UsernameRecoveryStartException
                    ? error
                    : UsernameRecoveryStartException.systemFailure(error));
  }

  private Mono<HydratedIdempotentResponse> executeAcquired(
      UsernameRecoveryStartCommand command, UsernameRecoveryStartValidator.Validation validation) {
    return operation
        .execute(command, validation)
        .map(result -> responses.toTransientResponse(result, HTTP_OK));
  }

  private Mono<UsernameRecoveryStartResult> route(
      IdempotencyReservationOutcome outcome, HydratedIdempotentResponse response) {
    return switch (outcome) {
      case ACQUIRED, COMPLETED_REPLAY -> Mono.just(responses.fromTransientResponse(response));
      case DUPLICATE_IN_PROGRESS ->
          Mono.error(UsernameRecoveryStartException.idempotencyInProgress());
      case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
          Mono.error(UsernameRecoveryStartException.keyReused());
      case FAILED_EXISTING -> Mono.error(UsernameRecoveryStartException.systemFailure());
      case UNKNOWN_OUTCOME_EXISTING ->
          Mono.error(UsernameRecoveryStartException.unknownOutcomeExisting());
    };
  }

  private static List<String> canonicalFields(
      UsernameRecoveryStartCommand command, UsernameRecoveryStartValidator.Validation validation) {
    return List.of(
        command.identityType().name(),
        command.identityValue(),
        command.dateOfBirth(),
        validation.email(),
        validation.mobile(),
        command.captchaChallengeId(),
        command.language().toLanguageTag());
  }
}
