package com.bct.ngtpa.apiservice.application.idempotency;

import java.time.Clock;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Shared orchestration. Only ACQUIRED executes; replay never falls through to execution. */
public final class DefaultIdempotencyExecutor implements IdempotencyExecutor {
  private final IdempotencyPort persistence;
  private final IdempotencyReservationTokenGenerator tokens;
  private final Clock clock;

  public DefaultIdempotencyExecutor(
      IdempotencyPort persistence, IdempotencyReservationTokenGenerator tokens, Clock clock) {
    this.persistence = Objects.requireNonNull(persistence);
    this.tokens = Objects.requireNonNull(tokens);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(command, "idempotent execution command must not be null");
          IdempotencyReservationToken proposed = tokens.generate();
          return persistence
              .reserve(
                  new IdempotencyCommands.Reserve(
                      command.operationId(),
                      command.fingerprint(),
                      proposed,
                      clock.instant(),
                      command.ttl()))
              .flatMap(reservation -> route(command, reservation));
        });
  }

  private Mono<IdempotentExecutionResult> route(
      IdempotentExecutionCommand command, IdempotencyReservation reservation) {
    return switch (reservation.outcome()) {
      case ACQUIRED -> executeOwned(command, reservation.reservationToken());
      case COMPLETED_REPLAY ->
          command
              .replayResolver()
              .hydrate(reservation.terminalResponse())
              .switchIfEmpty(
                  Mono.error(
                      new IdempotencyExecutionException(
                          "Shared idempotency replay hydration failed")))
              .map(
                  response ->
                      new IdempotentExecutionResult(
                          IdempotencyReservationOutcome.COMPLETED_REPLAY, response));
      case DUPLICATE_IN_PROGRESS,
          KEY_REUSED_WITH_DIFFERENT_REQUEST,
          FAILED_EXISTING,
          UNKNOWN_OUTCOME_EXISTING ->
          Mono.just(new IdempotentExecutionResult(reservation.outcome(), null));
    };
  }

  private Mono<IdempotentExecutionResult> executeOwned(
      IdempotentExecutionCommand command, IdempotencyReservationToken owner) {
    return Mono.defer(command.protectedOperation())
        .switchIfEmpty(
            Mono.error(
                new IdempotencyExecutionException(
                    "Protected idempotent operation returned no response")))
        .flatMap(response -> complete(command, owner, response))
        .onErrorResume(error -> classifyAndPersistFailure(command, owner, error));
  }

  private Mono<IdempotentExecutionResult> complete(
      IdempotentExecutionCommand command,
      IdempotencyReservationToken owner,
      HydratedIdempotentResponse response) {
    IdempotentCapturedResponse captured;
    try {
      captured = command.responseCapture().capture(response);
    } catch (RuntimeException failure) {
      return markUnknownThenError(command, owner, failure);
    }
    return persistence
        .complete(
            new IdempotencyCommands.Complete(
                command.operationId(),
                command.fingerprint(),
                owner,
                captured.snapshot(),
                clock.instant(),
                command.ttl()))
        .map(
            ignored ->
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.ACQUIRED, captured.response()))
        .onErrorResume(failure -> markUnknownThenError(command, owner, failure));
  }

  private Mono<IdempotentExecutionResult> classifyAndPersistFailure(
      IdempotentExecutionCommand command, IdempotencyReservationToken owner, Throwable failure) {
    IdempotencyFailureClassification classification;
    try {
      classification = command.failureClassifier().classify(failure);
    } catch (RuntimeException classifierFailure) {
      return markUnknownThenError(command, owner, failure);
    }
    if (classification == null
        || classification == IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT) {
      return markUnknownThenError(command, owner, failure);
    }
    return persistence
        .fail(
            new IdempotencyCommands.Fail(
                command.operationId(),
                command.fingerprint(),
                owner,
                clock.instant(),
                command.ttl()))
        .then(Mono.error(failure));
  }

  private Mono<IdempotentExecutionResult> markUnknownThenError(
      IdempotentExecutionCommand command, IdempotencyReservationToken owner, Throwable original) {
    return persistence
        .markUnknown(
            new IdempotencyCommands.MarkUnknown(
                command.operationId(),
                command.fingerprint(),
                owner,
                clock.instant(),
                command.ttl()))
        .then(Mono.<IdempotentExecutionResult>error(original))
        .onErrorMap(
            markFailure ->
                markFailure == original
                    ? markFailure
                    : new IdempotencyExecutionException(
                        "Shared idempotency unknown-outcome persistence failed", markFailure));
  }
}
