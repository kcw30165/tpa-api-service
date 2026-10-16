package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Shared Redis lifecycle adapter. Duplicate reads never extend TTL or disclose ownership. */
public final class RedisIdempotencyAdapter implements IdempotencyPort {
  private static final String TRANSITION_FAILED_MESSAGE = "Shared idempotency transition failed";

  private final RedisIdempotencyOperations redis;
  private final RedisIdempotencyKeyFactory keys;
  private final RedisIdempotencyJsonMapper json;

  public RedisIdempotencyAdapter(
      RedisIdempotencyOperations redis,
      RedisIdempotencyKeyFactory keys,
      RedisIdempotencyJsonMapper json) {
    this.redis = Objects.requireNonNull(redis);
    this.keys = Objects.requireNonNull(keys);
    this.json = Objects.requireNonNull(json);
  }

  @Override
  public Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve c) {
    return Mono.defer(
            () -> {
              var created =
                  new IdempotencyRecord(
                      c.operationId(),
                      c.fingerprint(),
                      c.reservationToken(),
                      IdempotencyStatus.PROCESSING,
                      null,
                      c.now(),
                      c.now(),
                      c.now().plus(c.ttl()));
              String key = keys.keyFor(c.operationId());
              String encoded = json.toJson(created);
              return redis
                  .setIfAbsent(key, encoded, c.ttl())
                  .flatMap(
                      acquired ->
                          acquired
                              ? Mono.just(
                                  new IdempotencyReservation(
                                      IdempotencyReservationOutcome.ACQUIRED,
                                      IdempotencyStatus.PROCESSING,
                                      null,
                                      c.reservationToken()))
                              : classifyExisting(key, c));
            })
        .onErrorMap(this::safe);
  }

  private Mono<IdempotencyReservation> classifyExisting(
      String key, IdempotencyCommands.Reserve command) {
    return redis
        .get(key)
        .flatMap(
            found -> {
              if (found.isEmpty())
                return Mono.error(new CacheException("Shared idempotency reserve failed"));
              IdempotencyRecord existing = json.fromJson(found.get());
              requireIdentity(command.operationId(), existing);
              if (!existing.expiresAt().isAfter(command.now())) {
                return Mono.error(new CacheException("Shared idempotency reserve failed"));
              }
              if (!existing.fingerprint().equals(command.fingerprint())) {
                return Mono.just(
                    new IdempotencyReservation(
                        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                        existing.status(),
                        null,
                        null));
              }
              return Mono.just(
                  switch (existing.status()) {
                    case PROCESSING ->
                        new IdempotencyReservation(
                            IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
                            existing.status(),
                            null,
                            null);
                    case COMPLETED ->
                        new IdempotencyReservation(
                            IdempotencyReservationOutcome.COMPLETED_REPLAY,
                            existing.status(),
                            existing.terminalResponse(),
                            null);
                    case FAILED ->
                        new IdempotencyReservation(
                            IdempotencyReservationOutcome.FAILED_EXISTING,
                            existing.status(),
                            null,
                            null);
                    case UNKNOWN_OUTCOME ->
                        new IdempotencyReservation(
                            IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
                            existing.status(),
                            null,
                            null);
                  });
            });
  }

  @Override
  public Mono<IdempotencyRecord> complete(IdempotencyCommands.Complete c) {
    return transition(
        c.operationId(),
        c.fingerprint(),
        c.reservationToken(),
        c.now(),
        IdempotencyStatus.COMPLETED,
        c.terminalResponse());
  }

  @Override
  public Mono<IdempotencyRecord> fail(IdempotencyCommands.Fail c) {
    return transition(
        c.operationId(),
        c.fingerprint(),
        c.reservationToken(),
        c.now(),
        IdempotencyStatus.FAILED,
        null);
  }

  @Override
  public Mono<IdempotencyRecord> markUnknown(IdempotencyCommands.MarkUnknown c) {
    return transition(
        c.operationId(),
        c.fingerprint(),
        c.reservationToken(),
        c.now(),
        IdempotencyStatus.UNKNOWN_OUTCOME,
        null);
  }

  private Mono<IdempotencyRecord> transition(
      IdempotencyOperationId operation,
      IdempotencyFingerprint fingerprint,
      IdempotencyReservationToken owner,
      Instant now,
      IdempotencyStatus target,
      IdempotentTerminalResponseSnapshot terminal) {
    String key = keys.keyFor(operation);
    return redis
        .get(key)
        .flatMap(
            found -> {
              if (found.isEmpty()) return Mono.error(new CacheException(TRANSITION_FAILED_MESSAGE));
              String currentJson = found.get();
              IdempotencyRecord current = json.fromJson(currentJson);
              requireIdentity(operation, current);
              if (!current.expiresAt().isAfter(now)
                  || !current.fingerprint().equals(fingerprint)
                  || !current.reservationToken().equals(owner)
                  || current.status() != IdempotencyStatus.PROCESSING) {
                return Mono.error(new CacheException(TRANSITION_FAILED_MESSAGE));
              }
              IdempotencyRecord replacement =
                  new IdempotencyRecord(
                      operation,
                      fingerprint,
                      owner,
                      target,
                      terminal,
                      current.createdAt(),
                      now,
                      current.expiresAt());
              String replacementJson = json.toJson(replacement);
              return redis
                  .transition(key, currentJson, replacementJson)
                  .filter(replacementJson::equals)
                  .switchIfEmpty(Mono.error(new CacheException(TRANSITION_FAILED_MESSAGE)))
                  .thenReturn(replacement);
            })
        .onErrorMap(this::safe);
  }

  @Override
  public Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId operation) {
    return redis
        .get(keys.keyFor(operation))
        .map(found -> found.map(json::fromJson))
        .onErrorMap(this::safe);
  }

  private static void requireIdentity(IdempotencyOperationId expected, IdempotencyRecord actual) {
    if (!expected.equals(actual.operationId())) {
      throw new CacheException("Shared idempotency state identity mismatch");
    }
  }

  private Throwable safe(Throwable error) {
    return error instanceof CacheException
        ? error
        : new CacheException("Shared idempotency persistence operation failed", error);
  }
}
