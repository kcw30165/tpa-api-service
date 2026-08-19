package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.*;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RedisCaptchaConsumptionAtomicityTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC);
  private static final CaptchaChallengeId ID = new CaptchaChallengeId("cap_abcdefghijklmnop");
  private static final CaptchaAnswerHash HASH = new CaptchaAnswerHash("0".repeat(64));
  private final RedisCaptchaChallengeJsonMapper json = new RedisCaptchaChallengeJsonMapper();

  @Test
  void competingConsumersHaveExactlyOneWinnerAndPreserveTtlAndMetadata() throws Exception {
    CaptchaChallenge verified = verified();
    RacingOperations redis = new RacingOperations(json.toJson(verified), Duration.ofSeconds(211));
    RedisCaptchaChallengeAdapter adapter = adapter(redis);
    CaptchaChallenge firstExpected = adapter.load(ID).block().orElseThrow();
    CaptchaChallenge secondExpected = adapter.load(ID).block().orElseThrow();
    var first = new java.util.concurrent.atomic.AtomicReference<CaptchaConsumptionMutationResult>();
    var second =
        new java.util.concurrent.atomic.AtomicReference<CaptchaConsumptionMutationResult>();
    Thread one =
        new Thread(
            () ->
                first.set(adapter.applyConsumptionAttempt(firstExpected, CLOCK.instant()).block()));
    Thread two =
        new Thread(
            () ->
                second.set(
                    adapter
                        .applyConsumptionAttempt(secondExpected, CLOCK.instant().plusSeconds(1))
                        .block()));
    one.start();
    two.start();
    one.join();
    two.join();
    assertThat(java.util.List.of(first.get().outcome(), second.get().outcome()))
        .containsExactlyInAnyOrder(
            CaptchaConsumptionMutationResult.Outcome.CONSUMED,
            CaptchaConsumptionMutationResult.Outcome.STATE_CHANGED);
    CaptchaChallenge stored = json.fromJson(redis.value);
    assertThat(stored.status()).isEqualTo(CaptchaStatus.CONSUMED);
    assertThat(stored.answerHash()).isNull();
    assertThat(stored.verifiedAt()).isEqualTo(verified.verifiedAt());
    assertThat(stored.failedAttemptCount()).isEqualTo(verified.failedAttemptCount());
    assertThat(redis.ttl).isEqualTo(Duration.ofSeconds(211));
    assertThat(redis.applied.get()).isEqualTo(1);
  }

  @Test
  void missingAndConflictAreStrictNonSuccessOutcomes() {
    RacingOperations missing = new RacingOperations(null, Duration.ofSeconds(10));
    assertThat(
            adapter(missing).applyConsumptionAttempt(verified(), CLOCK.instant()).block().outcome())
        .isEqualTo(CaptchaConsumptionMutationResult.Outcome.NOT_FOUND);
    RacingOperations conflict =
        new RacingOperations(json.toJson(consumed()), Duration.ofSeconds(10));
    assertThat(
            adapter(conflict)
                .applyConsumptionAttempt(verified(), CLOCK.instant())
                .block()
                .outcome())
        .isEqualTo(CaptchaConsumptionMutationResult.Outcome.STATE_CHANGED);
    assertThat(json.fromJson(conflict.value).status()).isEqualTo(CaptchaStatus.CONSUMED);
  }

  private RedisCaptchaChallengeAdapter adapter(RacingOperations operations) {
    return new RedisCaptchaChallengeAdapter(
        new RedisCacheKeyFactory("ngtpa"), json, operations, CLOCK);
  }

  private CaptchaChallenge verified() {
    return new CaptchaChallenge(
        ID,
        HASH,
        CaptchaStatus.VERIFIED,
        2,
        5,
        CLOCK.instant().minusSeconds(60),
        CLOCK.instant().plusSeconds(300),
        CLOCK.instant().minusSeconds(10),
        null,
        null,
        null);
  }

  private CaptchaChallenge consumed() {
    CaptchaChallenge v = verified();
    return new CaptchaChallenge(
        ID,
        null,
        CaptchaStatus.CONSUMED,
        v.failedAttemptCount(),
        v.maxAttempts(),
        v.createdAt(),
        v.expiresAt(),
        v.verifiedAt(),
        CLOCK.instant(),
        null,
        null);
  }

  private static final class RacingOperations implements RedisCaptchaChallengeOperations {
    private String value;
    private final Duration ttl;
    private final AtomicInteger applied = new AtomicInteger();
    private final Object lock = new Object();

    RacingOperations(String value, Duration ttl) {
      this.value = value;
      this.ttl = ttl;
    }

    public Mono<Boolean> setIfAbsent(String k, String v, Duration t) {
      return Mono.just(false);
    }

    public Mono<Void> set(String k, String v, Duration t) {
      value = v;
      return Mono.empty();
    }

    public Mono<Optional<String>> get(String k) {
      return Mono.just(Optional.ofNullable(value));
    }

    public Mono<Optional<Duration>> ttl(String k) {
      return Mono.just(Optional.ofNullable(ttl));
    }

    public Mono<Void> delete(String k) {
      value = null;
      return Mono.empty();
    }

    public Mono<AtomicReplaceResult> replaceIfUnchanged(
        String k, String expected, String replacement) {
      return Mono.fromSupplier(
          () -> {
            synchronized (lock) {
              if (value == null) return AtomicReplaceResult.MISSING;
              if (!value.equals(expected)) return AtomicReplaceResult.CONFLICT;
              value = replacement;
              applied.incrementAndGet();
              return AtomicReplaceResult.APPLIED;
            }
          });
    }
  }
}
