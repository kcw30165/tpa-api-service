package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.Status;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

/**
 * Redis persistence adapter for the Username Recovery idempotency port.
 *
 * <p>Atomic reservation uses the generic native SET-NX convention of the existing
 * registration/forgot-password idempotency adapters (no feature Lua). Completion and
 * unknown-outcome transitions are ownership-checked atomically by the feature script {@code
 * redis/username-recovery-idempotency/transition.lua}. Redis, serialization, and script failures
 * always surface as sanitized {@link CacheException} and are never collapsed into absence,
 * reservation, replay, or success.
 */
public class RedisUsernameRecoveryIdempotencyAdapter implements UsernameRecoveryIdempotencyPort {

  private static final DefaultRedisScript<String> TRANSITION =
      script("redis/username-recovery-idempotency/transition.lua");

  private final ReactiveStringRedisTemplate redis;
  private final RedisCacheKeyFactory keyFactory;
  private final RedisUsernameRecoveryIdempotencyJsonMapper jsonMapper;

  public RedisUsernameRecoveryIdempotencyAdapter(
      ReactiveStringRedisTemplate redis,
      RedisCacheKeyFactory keyFactory,
      RedisUsernameRecoveryIdempotencyJsonMapper jsonMapper) {
    this.redis = Objects.requireNonNull(redis, "redis must not be null");
    this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
  }

  @Override
  public Mono<ReservationResult> reserve(ReserveCommand command) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(command, "command must not be null");
          String key = keyFor(command.action(), command.idempotencyKey());
          var record =
              new UsernameRecoveryIdempotencyRecord(
                  command.action(),
                  command.idempotencyKey(),
                  command.fingerprint(),
                  command.operationReference(),
                  Status.IN_PROGRESS,
                  null,
                  command.now(),
                  command.now(),
                  command.expiresAt());
          String json = jsonMapper.toJson(record);
          return redis
              .opsForValue()
              .setIfAbsent(key, json, command.ttl())
              .map(Boolean.TRUE::equals)
              .switchIfEmpty(
                  Mono.error(new CacheException("Username recovery idempotency reserve failed")))
              .flatMap(
                  created ->
                      created
                          ? Mono.just(
                              new ReservationResult(
                                  UsernameRecoveryIdempotencyOutcome.RESERVED,
                                  reservation(command)))
                          : classifyExisting(key, command))
              .onErrorMap(error -> persistenceFailure("reserve", error));
        });
  }

  @Override
  public Mono<Optional<UsernameRecoveryIdempotencyRecord>> resolve(
      UsernameRecoveryIdempotencyAction action, UsernameRecoveryIdempotencyKey idempotencyKey) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(action, "action must not be null");
          Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
          String key = keyFor(action, idempotencyKey);
          return redis
              .opsForValue()
              .get(key)
              .map(json -> Optional.of(jsonMapper.fromJson(json)))
              .defaultIfEmpty(Optional.empty())
              .onErrorMap(error -> persistenceFailure("resolve", error));
        });
  }

  @Override
  public Mono<UsernameRecoveryIdempotencyRecord> complete(CompleteCommand command) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(command, "command must not be null");
          String key = keyFor(command.action(), command.idempotencyKey());
          return redis
              .execute(
                  TRANSITION,
                  List.of(key),
                  List.of(
                      command.action().name(),
                      command.idempotencyKey().value(),
                      command.fingerprint().value(),
                      command.operationReference().value(),
                      Status.COMPLETED.name(),
                      command.now().toString(),
                      command.replaySnapshot().value()))
              .next()
              .map(result -> mapTransitionResult("complete", result))
              .switchIfEmpty(
                  Mono.error(new CacheException("Username recovery idempotency complete failed")))
              .onErrorMap(error -> persistenceFailure("complete", error));
        });
  }

  @Override
  public Mono<UsernameRecoveryIdempotencyRecord> recordUnknownOutcome(
      UnknownOutcomeCommand command) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(command, "command must not be null");
          String key = keyFor(command.action(), command.idempotencyKey());
          return redis
              .execute(
                  TRANSITION,
                  List.of(key),
                  List.of(
                      command.action().name(),
                      command.idempotencyKey().value(),
                      command.fingerprint().value(),
                      command.operationReference().value(),
                      Status.UNKNOWN_OUTCOME.name(),
                      command.now().toString(),
                      ""))
              .next()
              .map(result -> mapTransitionResult("unknown outcome", result))
              .switchIfEmpty(
                  Mono.error(
                      new CacheException("Username recovery idempotency unknown outcome failed")))
              .onErrorMap(error -> persistenceFailure("unknown outcome", error));
        });
  }

  private Mono<ReservationResult> classifyExisting(String key, ReserveCommand command) {
    return redis
        .opsForValue()
        .get(key)
        .map(
            json ->
                new ReservationResult(outcomeForExisting(jsonMapper.fromJson(json), command), null))
        .switchIfEmpty(
            Mono.error(new CacheException("Username recovery idempotency reserve failed")));
  }

  private UsernameRecoveryIdempotencyRecord mapTransitionResult(String operation, String result) {
    if (result == null || result.isBlank()) {
      throw new CacheException("Username recovery idempotency " + operation + " failed");
    }
    return switch (result) {
      case "MISSING", "CONFLICT", "EXPIRED", "CORRUPT" ->
          throw new CacheException("Username recovery idempotency " + operation + " failed");
      default -> jsonMapper.fromJson(result);
    };
  }

  private static UsernameRecoveryIdempotencyOutcome outcomeForExisting(
      UsernameRecoveryIdempotencyRecord existing, ReserveCommand command) {
    if (!existing.fingerprint().equals(command.fingerprint())) {
      return UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;
    }
    return switch (existing.status()) {
      case IN_PROGRESS -> UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS;
      case COMPLETED -> UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY;
      case UNKNOWN_OUTCOME -> UsernameRecoveryIdempotencyOutcome.UNKNOWN_OUTCOME_EXISTING;
    };
  }

  private static UsernameRecoveryIdempotencyReservation reservation(ReserveCommand command) {
    return new UsernameRecoveryIdempotencyReservation(
        command.action(),
        command.idempotencyKey(),
        command.fingerprint(),
        command.operationReference(),
        command.expiresAt());
  }

  private String keyFor(
      UsernameRecoveryIdempotencyAction action, UsernameRecoveryIdempotencyKey idempotencyKey) {
    return RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(keyFactory, action, idempotencyKey);
  }

  private static CacheException persistenceFailure(String operation, Throwable error) {
    if (error instanceof CacheException cacheException) {
      return cacheException;
    }
    return new CacheException("Username recovery idempotency " + operation + " failed", error);
  }

  private static DefaultRedisScript<String> script(String path) {
    DefaultRedisScript<String> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(path));
    script.setResultType(String.class);
    return script;
  }
}
