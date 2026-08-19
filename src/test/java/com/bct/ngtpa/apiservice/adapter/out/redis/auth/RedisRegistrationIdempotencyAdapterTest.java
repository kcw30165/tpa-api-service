package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisRegistrationIdempotencyAdapterTest {
  private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");
  private static final String HASH =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String OTHER_HASH =
      "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

  private final InMemoryRedisOperations redis = new InMemoryRedisOperations();
  private final RedisRegistrationIdempotencyAdapter adapter =
      new RedisRegistrationIdempotencyAdapter(
          new RedisRegistrationIdempotencyKeyFactory("ngtpa"),
          new RedisRegistrationIdempotencyJsonMapper(),
          redis);

  @Test
  void startProcessingCreatesProcessingRecordWhenAbsent() {
    StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(RegistrationIdempotencyStartOutcome.STARTED);
              assertThat(result.record().status())
                  .isEqualTo(RegistrationIdempotencyStatus.PROCESSING);
              assertThat(result.record().action())
                  .isEqualTo(RegistrationIdempotencyAction.REGISTRATION_START);
              assertThat(result.record().requestHash())
                  .isEqualTo(new RegistrationRequestHash(HASH));
            })
        .verifyComplete();

    assertThat(redis.ttlFor("ngtpa:auth:idempotency:registration-start:idem_abc123"))
        .isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void startProcessingReturnsDuplicateInProgressForSameHash() {
    adapter.startProcessing(startCommand(HASH)).block();

    StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
        .assertNext(
            result ->
                assertThat(result.outcome())
                    .isEqualTo(RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS))
        .verifyComplete();
    assertThat(redis.setIfAbsentCalls).isEqualTo(2);
    assertThat(redis.setCalls).isZero();
  }

  @Test
  void startProcessingReturnsCompletedReplayForSameHash() {
    adapter.startProcessing(startCommand(HASH)).block();
    adapter.markCompleted(completeCommand(HASH)).block();

    StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY);
              assertThat(result.record().responseSnapshot()).isNotNull();
            })
        .verifyComplete();
  }

  @Test
  void startProcessingReturnsFailedExistingForSameHash() {
    adapter.startProcessing(startCommand(HASH)).block();
    adapter.markFailed(failCommand(HASH)).block();

    StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
        .assertNext(
            result ->
                assertThat(result.outcome())
                    .isEqualTo(RegistrationIdempotencyStartOutcome.FAILED_EXISTING))
        .verifyComplete();
  }

  @Test
  void startProcessingRejectsKeyReuseWithDifferentHash() {
    adapter.startProcessing(startCommand(HASH)).block();

    StepVerifier.create(adapter.startProcessing(startCommand(OTHER_HASH)))
        .assertNext(
            result ->
                assertThat(result.outcome())
                    .isEqualTo(
                        RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST))
        .verifyComplete();
    assertThat(redis.setCalls).isZero();
  }

  @Test
  void markCompletedStoresSnapshotAndTtl() {
    adapter.startProcessing(startCommand(HASH)).block();

    StepVerifier.create(adapter.markCompleted(completeCommand(HASH)))
        .assertNext(
            record -> {
              assertThat(record.status()).isEqualTo(RegistrationIdempotencyStatus.COMPLETED);
              assertThat(record.responseSnapshot()).isNotNull();
              assertThat(record.updatedAt()).isEqualTo(NOW.plusSeconds(10));
            })
        .verifyComplete();
    assertThat(redis.ttlFor("ngtpa:auth:idempotency:registration-start:idem_abc123"))
        .isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void markCompletedRejectsDifferentRequestHash() {
    adapter.startProcessing(startCommand(HASH)).block();

    StepVerifier.create(adapter.markCompleted(completeCommand(OTHER_HASH)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Registration idempotency request hash mismatch")
                    .hasMessageNotContaining("idem_abc123")
                    .hasMessageNotContaining(HASH)
                    .hasMessageNotContaining(OTHER_HASH)
                    .hasMessageNotContaining("ngtpa:auth:idempotency"))
        .verify();
  }

  @Test
  void markCompletedRejectsMissingRecord() {
    StepVerifier.create(adapter.markCompleted(completeCommand(HASH)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Registration idempotency record missing"))
        .verify();
    assertThat(redis.store).isEmpty();
  }

  @Test
  void markFailedStoresFailedStatus() {
    adapter.startProcessing(startCommand(HASH)).block();

    StepVerifier.create(adapter.markFailed(failCommand(HASH)))
        .assertNext(
            record -> {
              assertThat(record.status()).isEqualTo(RegistrationIdempotencyStatus.FAILED);
              assertThat(record.responseSnapshot()).isNull();
              assertThat(record.updatedAt()).isEqualTo(NOW.plusSeconds(20));
            })
        .verifyComplete();
    assertThat(redis.ttlFor("ngtpa:auth:idempotency:registration-start:idem_abc123"))
        .isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void loadReturnsEmptyWhenMissing() {
    StepVerifier.create(
            adapter.load(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_abc123")))
        .expectNext(Optional.empty())
        .verifyComplete();
  }

  @Test
  void loadsExistingRecordRoundTrip() {
    adapter.startProcessing(startCommand(HASH)).block();
    adapter.markCompleted(completeCommand(HASH)).block();

    StepVerifier.create(
            adapter.load(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_abc123")))
        .assertNext(
            optional -> {
              assertThat(optional).isPresent();
              assertThat(optional.get().action())
                  .isEqualTo(RegistrationIdempotencyAction.REGISTRATION_START);
              assertThat(optional.get().status())
                  .isEqualTo(RegistrationIdempotencyStatus.COMPLETED);
              assertThat(optional.get().responseSnapshot().safeHeaders())
                  .containsEntry("X-Safe", "value");
            })
        .verifyComplete();
  }

  @Test
  void deleteRemovesRecord() {
    adapter.startProcessing(startCommand(HASH)).block();

    StepVerifier.create(
            adapter.delete(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_abc123")))
        .verifyComplete();
    StepVerifier.create(
            adapter.load(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_abc123")))
        .expectNext(Optional.empty())
        .verifyComplete();
    StepVerifier.create(
            adapter.delete(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_abc123")))
        .verifyComplete();
  }

  @Test
  void redisFailureDoesNotExposeKeyHashOrSnapshot() {
    var failingRedis = new FailingRedisOperations();
    var failingAdapter =
        new RedisRegistrationIdempotencyAdapter(
            new RedisRegistrationIdempotencyKeyFactory("ngtpa"),
            new RedisRegistrationIdempotencyJsonMapper(),
            failingRedis);

    StepVerifier.create(failingAdapter.startProcessing(startCommand(HASH)))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Redis registration idempotency operation failed")
                    .hasMessageNotContaining("idem_abc123")
                    .hasMessageNotContaining(
                        "ngtpa:auth:idempotency:registration-start:idem_abc123")
                    .hasMessageNotContaining(HASH)
                    .hasMessageNotContaining("serialized"))
        .verify();
  }

  private static RegistrationIdempotencyStartCommand startCommand(String hash) {
    return new RegistrationIdempotencyStartCommand(
        RegistrationIdempotencyAction.REGISTRATION_START,
        new RegistrationIdempotencyKey("idem_abc123"),
        new RegistrationRequestHash(hash),
        NOW,
        Duration.ofMinutes(5));
  }

  private static RegistrationIdempotencyCompleteCommand completeCommand(String hash) {
    return new RegistrationIdempotencyCompleteCommand(
        RegistrationIdempotencyAction.REGISTRATION_START,
        new RegistrationIdempotencyKey("idem_abc123"),
        new RegistrationRequestHash(hash),
        new RegistrationIdempotencyResponseSnapshot(
            "200", "{\"ok\":true}", Map.of("X-Safe", "value")),
        NOW.plusSeconds(10),
        Duration.ofMinutes(10));
  }

  private static RegistrationIdempotencyFailCommand failCommand(String hash) {
    return new RegistrationIdempotencyFailCommand(
        RegistrationIdempotencyAction.REGISTRATION_START,
        new RegistrationIdempotencyKey("idem_abc123"),
        new RegistrationRequestHash(hash),
        "workflow.system.unavailable",
        NOW.plusSeconds(20),
        Duration.ofMinutes(2));
  }

  private static final class InMemoryRedisOperations
      implements RedisRegistrationIdempotencyOperations {
    private final Map<String, String> store = new LinkedHashMap<>();
    private final Map<String, Duration> ttls = new LinkedHashMap<>();
    private int setIfAbsentCalls;
    private int setCalls;

    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
      setIfAbsentCalls++;
      if (store.containsKey(key)) {
        return Mono.just(false);
      }
      store.put(key, value);
      ttls.put(key, ttl);
      return Mono.just(true);
    }

    @Override
    public Mono<Void> set(String key, String value, Duration ttl) {
      setCalls++;
      store.put(key, value);
      ttls.put(key, ttl);
      return Mono.empty();
    }

    @Override
    public Mono<Optional<String>> get(String key) {
      return Mono.just(Optional.ofNullable(store.get(key)));
    }

    @Override
    public Mono<Void> delete(String key) {
      store.remove(key);
      ttls.remove(key);
      return Mono.empty();
    }

    Duration ttlFor(String key) {
      return ttls.get(key);
    }
  }

  private static final class FailingRedisOperations
      implements RedisRegistrationIdempotencyOperations {
    @Override
    public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
      return Mono.error(new RuntimeException("boom " + key));
    }

    @Override
    public Mono<Void> set(String key, String value, Duration ttl) {
      return Mono.error(new RuntimeException("boom " + key));
    }

    @Override
    public Mono<Optional<String>> get(String key) {
      return Mono.error(new RuntimeException("boom " + key));
    }

    @Override
    public Mono<Void> delete(String key) {
      return Mono.error(new RuntimeException("boom " + key));
    }
  }
}
