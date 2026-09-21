package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RedisIdempotencyAdapterTest {
  private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

  @Test
  void oneOwnerTransitionsAndDuplicateNeverReceivesToken() {
    FakeRedis redis = new FakeRedis();
    var adapter = adapter(redis);
    var first = adapter.reserve(reserve(token("ownerToken0123456789abcdefghijklm"))).block();
    var duplicate = adapter.reserve(reserve(token("otherToken0123456789abcdefghijk"))).block();
    assertThat(first.outcome()).isEqualTo(IdempotencyReservationOutcome.ACQUIRED);
    assertThat(first.reservationToken()).isNotNull();
    assertThat(duplicate.outcome()).isEqualTo(IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS);
    assertThat(duplicate.reservationToken()).isNull();
    var completed =
        adapter
            .complete(
                new IdempotencyCommands.Complete(
                    op(),
                    fp(),
                    first.reservationToken(),
                    snapshot(),
                    NOW.plusSeconds(1),
                    Duration.ofMinutes(5)))
            .block();
    assertThat(completed.status()).isEqualTo(IdempotencyStatus.COMPLETED);
    assertThatThrownBy(
            () ->
                adapter
                    .fail(
                        new IdempotencyCommands.Fail(
                            op(),
                            fp(),
                            token("otherToken0123456789abcdefghijk"),
                            NOW.plusSeconds(2),
                            Duration.ofMinutes(5)))
                    .block())
        .hasMessageContaining("transition failed");
  }

  @Test
  void differentFingerprintIsKeyReuseAndReplayDoesNotExposeOwner() {
    FakeRedis redis = new FakeRedis();
    var adapter = adapter(redis);
    var owner = token("ownerToken0123456789abcdefghijklm");
    adapter.reserve(reserve(owner)).block();
    var mismatch =
        adapter
            .reserve(
                new IdempotencyCommands.Reserve(
                    op(),
                    new IdempotencyFingerprint(1, "b".repeat(64)),
                    token("otherToken0123456789abcdefghijk"),
                    NOW,
                    Duration.ofMinutes(5)))
            .block();
    assertThat(mismatch.outcome())
        .isEqualTo(IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST);
    adapter
        .complete(
            new IdempotencyCommands.Complete(
                op(), fp(), owner, snapshot(), NOW.plusSeconds(1), Duration.ofMinutes(5)))
        .block();
    var replay = adapter.reserve(reserve(token("thirdToken0123456789abcdefghijk"))).block();
    assertThat(replay.outcome()).isEqualTo(IdempotencyReservationOutcome.COMPLETED_REPLAY);
    assertThat(replay.reservationToken()).isNull();
  }

  private static RedisIdempotencyAdapter adapter(FakeRedis redis) {
    byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    return new RedisIdempotencyAdapter(
        redis,
        new RedisIdempotencyKeyFactory(
            new RedisCacheKeyFactory("ngtpa"),
            new HmacSha256IdempotencyPersistenceIdDeriver(secret)),
        new RedisIdempotencyJsonMapper());
  }

  private static IdempotencyCommands.Reserve reserve(IdempotencyReservationToken t) {
    return new IdempotencyCommands.Reserve(op(), fp(), t, NOW, Duration.ofMinutes(5));
  }

  private static IdempotencyOperationId op() {
    return new IdempotencyOperationId(
        new IdempotencyCapability("profile"),
        new IdempotencyAction("update-details"),
        new IdempotencyOperationScope("opaqueScope01"),
        new IdempotencyKey("requestKey01"));
  }

  private static IdempotencyFingerprint fp() {
    return new IdempotencyFingerprint(1, "a".repeat(64));
  }

  private static IdempotencyReservationToken token(String s) {
    String validToken = s.length() >= 32 ? s : s + "x".repeat(32 - s.length());
    return new IdempotencyReservationToken(validToken);
  }

  private static IdempotentTerminalResponseSnapshot snapshot() {
    return new IdempotentTerminalResponseSnapshot(1, 200, "application/json", "{}", null, null);
  }

  static final class FakeRedis implements RedisIdempotencyOperations {
    String value;
    final AtomicInteger writes = new AtomicInteger();

    public Mono<Boolean> setIfAbsent(String k, String v, Duration ttl) {
      if (value != null) return Mono.just(false);
      value = v;
      writes.incrementAndGet();
      return Mono.just(true);
    }

    public Mono<Optional<String>> get(String k) {
      return Mono.just(Optional.ofNullable(value));
    }

    public Mono<String> transition(String k, String expected, String replacement) {
      if (!expected.equals(value)) return Mono.just("CONFLICT");
      value = replacement;
      writes.incrementAndGet();
      return Mono.just(replacement);
    }
  }

  // ----- markUnknown() and load() (added round 182 - not covered by the
  // original file's 2 tests, which only exercise reserve/complete/fail) -----------

  @Test
  void markUnknownTransitionsAndLoadReturnsRecord() {
    FakeRedis redis = new FakeRedis();
    var adapter = adapter(redis);
    var reserved = adapter.reserve(reserve(token("ownerToken0123456789abcdefghijklm"))).block();
    var unknown =
        adapter
            .markUnknown(
                new IdempotencyCommands.MarkUnknown(
                    op(), fp(), reserved.reservationToken(), NOW.plusSeconds(1), Duration.ofMinutes(5)))
            .block();
    assertThat(unknown.status()).isEqualTo(IdempotencyStatus.UNKNOWN_OUTCOME);

    var loaded = adapter.load(op()).block();
    assertThat(loaded).isPresent();
    assertThat(loaded.get().status()).isEqualTo(IdempotencyStatus.UNKNOWN_OUTCOME);
  }

  @Test
  void loadMissingRecordReturnsEmpty() {
    FakeRedis redis = new FakeRedis();
    var adapter = adapter(redis);
    var loaded = adapter.load(op()).block();
    assertThat(loaded).isEmpty();
  }

  @Test
  void unknownOutcomeExistingIsClassifiedOnReReserve() {
    FakeRedis redis = new FakeRedis();
    var adapter = adapter(redis);
    var reserved = adapter.reserve(reserve(token("ownerToken0123456789abcdefghijklm"))).block();
    adapter
        .markUnknown(
            new IdempotencyCommands.MarkUnknown(
                op(), fp(), reserved.reservationToken(), NOW.plusSeconds(1), Duration.ofMinutes(5)))
        .block();
    var reReserve = adapter.reserve(reserve(token("otherToken0123456789abcdefghijk"))).block();
    assertThat(reReserve.outcome())
        .isEqualTo(IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING);
  }

  @Test
  void failedExistingIsClassifiedOnReReserve() {
    FakeRedis redis = new FakeRedis();
    var adapter = adapter(redis);
    var reserved = adapter.reserve(reserve(token("ownerToken0123456789abcdefghijklm"))).block();
    adapter
        .fail(
            new IdempotencyCommands.Fail(
                op(), fp(), reserved.reservationToken(), NOW.plusSeconds(1), Duration.ofMinutes(5)))
        .block();
    var reReserve = adapter.reserve(reserve(token("otherToken0123456789abcdefghijk"))).block();
    assertThat(reReserve.outcome()).isEqualTo(IdempotencyReservationOutcome.FAILED_EXISTING);
  }

  @Test
  void expiredExistingRecordOnReserveErrors() {
    FakeRedis redis = new FakeRedis();
    var adapter = adapter(redis);
    adapter
        .reserve(
            new IdempotencyCommands.Reserve(
                op(), fp(), token("ownerToken0123456789abcdefghijklm"), NOW, Duration.ofSeconds(1)))
        .block();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                adapter
                    .reserve(
                        new IdempotencyCommands.Reserve(
                            op(), fp(), token("otherToken0123456789abcdefghijk"),
                            NOW.plusSeconds(10), Duration.ofMinutes(5)))
                    .block())
        .hasMessageContaining("reserve failed");
  }
}
