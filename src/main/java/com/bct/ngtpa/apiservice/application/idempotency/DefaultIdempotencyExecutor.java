package com.bct.ngtpa.apiservice.application.idempotency;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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

  @Override
  public Mono<Optional<IdempotentExecutionResult>> replayCompleted(
      IdempotencyOperationId operationId,
      IdempotencyFingerprint fingerprint,
      ProtectedReplayResolver replayResolver) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(operationId, "operation id must not be null");
          Objects.requireNonNull(fingerprint, "fingerprint must not be null");
          Objects.requireNonNull(replayResolver, "replay resolver must not be null");
          Instant now = clock.instant();
          return persistence
              .load(operationId)
              .switchIfEmpty(
                  Mono.error(
                      new IdempotencyExecutionException(
                          "Shared idempotency load returned no result")))
              .flatMap(
                  found -> {
                    if (found.isEmpty()) return Mono.just(Optional.empty());
                    IdempotencyRecord record = found.orElseThrow();
                    if (!record.operationId().equals(operationId)
                        || !record.fingerprint().equals(fingerprint)
                        || !record.expiresAt().isAfter(now)
                        || record.status() != IdempotencyStatus.COMPLETED
                        || record.terminalResponse() == null) {
                      return Mono.just(Optional.empty());
                    }
                    return hydrateCompleted(record.terminalResponse(), replayResolver)
                        .map(Optional::of);
                  });
        });
  }

  private Mono<IdempotentExecutionResult> route(
      IdempotentExecutionCommand command, IdempotencyReservation reservation) {
    return switch (reservation.outcome()) {
      case ACQUIRED -> executeOwned(command, reservation.reservationToken());
      case COMPLETED_REPLAY ->
          hydrateCompleted(reservation.terminalResponse(), command.replayResolver());
      case DUPLICATE_IN_PROGRESS,
          KEY_REUSED_WITH_DIFFERENT_REQUEST,
          FAILED_EXISTING,
          UNKNOWN_OUTCOME_EXISTING ->
          Mono.just(new IdempotentExecutionResult(reservation.outcome(), null));
    };
  }

  private Mono<IdempotentExecutionResult> hydrateCompleted(
      IdempotentTerminalResponseSnapshot snapshot, ProtectedReplayResolver replayResolver) {
    return replayResolver
        .hydrate(snapshot)
        .switchIfEmpty(
            Mono.error(
                new IdempotencyExecutionException("Shared idempotency replay hydration failed")))
        .map(
            response ->
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.COMPLETED_REPLAY, response));
  }

  private Mono<IdempotentExecutionResult> executeOwned(
      IdempotentExecutionCommand command, IdempotencyReservationToken owner) {
    return executeProtected(command, owner).flatMap(response -> complete(command, owner, response));
  }

  private Mono<HydratedIdempotentResponse> executeProtected(
      IdempotentExecutionCommand command, IdempotencyReservationToken owner) {
    return Mono.defer(command.protectedOperation())
        .switchIfEmpty(
            Mono.error(
                new IdempotencyExecutionException(
                    "Protected idempotent operation returned no response")))
        .onErrorResume(failure -> classifyProtectedFailureThenError(command, owner, failure));
  }

  private Mono<IdempotentExecutionResult> complete(
      IdempotentExecutionCommand command,
      IdempotencyReservationToken owner,
      HydratedIdempotentResponse response) {
    return Mono.defer(() -> command.responseCapture().capture(response))
        .switchIfEmpty(
            Mono.error(
                new IdempotencyExecutionException(
                    "Protected idempotent response capture returned no result")))
        .flatMap(
            captured ->
                persistence
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
                                IdempotencyReservationOutcome.ACQUIRED, captured.response())))
        .onErrorResume(failure -> markUnknownThenError(command, owner, failure));
  }

  private Mono<HydratedIdempotentResponse> classifyProtectedFailureThenError(
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
        .then(Mono.<HydratedIdempotentResponse>error(failure));
  }

  private <T> Mono<T> markUnknownThenError(
      IdempotentExecutionCommand command, IdempotencyReservationToken owner, Throwable original) {
    return persistence
        .markUnknown(
            new IdempotencyCommands.MarkUnknown(
                command.operationId(),
                command.fingerprint(),
                owner,
                clock.instant(),
                command.ttl()))
        .then(Mono.<T>error(original))
        .onErrorMap(
            markFailure ->
                markFailure == original
                    ? markFailure
                    : new IdempotencyExecutionException(
                        "Shared idempotency unknown-outcome persistence failed", markFailure));
  }
}
