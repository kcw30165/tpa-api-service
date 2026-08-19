package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisCaptchaVerificationAtomicityTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC);
  private static final CaptchaChallengeId ID = new CaptchaChallengeId("cap_abcdefghijklmnop");
  private static final CaptchaAnswerHash HASH = new CaptchaAnswerHash("0".repeat(64));
  private final RedisCaptchaChallengeJsonMapper json = new RedisCaptchaChallengeJsonMapper();

  @Test
  void correctTransitionIsConditionalAndPreservesTtl() {
    FakeOperations redis = new FakeOperations(created(0), Duration.ofSeconds(271), json);
    var adapter = adapter(redis);
    var expected = adapter.load(ID).block().orElseThrow();
    var first = adapter.applyVerificationAttempt(expected, true, CLOCK.instant()).block();
    var second = adapter.applyVerificationAttempt(expected, true, CLOCK.instant()).block();
    assertThat(first.outcome()).isEqualTo(CaptchaVerificationMutationResult.Outcome.VERIFIED);
    assertThat(second.outcome()).isEqualTo(CaptchaVerificationMutationResult.Outcome.STATE_CHANGED);
    assertThat(redis.ttl).isEqualTo(Duration.ofSeconds(271));
  }

  @Test
  void competingWrongAttemptsDoNotLoseIncrementAndLockAtBoundary() {
    FakeOperations redis = new FakeOperations(created(3), Duration.ofSeconds(199), json);
    var adapter = adapter(redis);
    var stale = adapter.load(ID).block().orElseThrow();
    var first = adapter.applyVerificationAttempt(stale, false, CLOCK.instant()).block();
    var conflict = adapter.applyVerificationAttempt(stale, false, CLOCK.instant()).block();
    var current = adapter.load(ID).block().orElseThrow();
    var second = adapter.applyVerificationAttempt(current, false, CLOCK.instant()).block();
    assertThat(first.challenge().failedAttemptCount()).isEqualTo(4);
    assertThat(conflict.outcome())
        .isEqualTo(CaptchaVerificationMutationResult.Outcome.STATE_CHANGED);
    assertThat(second.outcome()).isEqualTo(CaptchaVerificationMutationResult.Outcome.LOCKED);
    assertThat(second.challenge().failedAttemptCount()).isEqualTo(5);
    assertThat(redis.ttl).isEqualTo(Duration.ofSeconds(199));
  }

  @Test
  void terminalOrMissingStateCannotBeOverwrittenAndFailurePropagates() {
    FakeOperations redis = new FakeOperations(created(0), Duration.ofSeconds(100), json);
    var adapter = adapter(redis);
    var stale = adapter.load(ID).block().orElseThrow();
    redis.replace(status(CaptchaStatus.CONSUMED, 0));
    StepVerifier.create(adapter.applyVerificationAttempt(stale, true, CLOCK.instant()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(CaptchaVerificationMutationResult.Outcome.STATE_CHANGED))
        .verifyComplete();
    assertThat(json.fromJson(redis.value).status()).isEqualTo(CaptchaStatus.CONSUMED);
    redis.value = null;
    StepVerifier.create(adapter.applyVerificationAttempt(stale, true, CLOCK.instant()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(CaptchaVerificationMutationResult.Outcome.NOT_FOUND))
        .verifyComplete();
    redis.failure = new RuntimeException("boom");
    StepVerifier.create(adapter.applyVerificationAttempt(stale, true, CLOCK.instant()))
        .expectError()
        .verify();
  }

  private RedisCaptchaChallengeAdapter adapter(FakeOperations redis) {
    return new RedisCaptchaChallengeAdapter(new RedisCacheKeyFactory("ngtpa"), json, redis, CLOCK);
  }

  private CaptchaChallenge created(int attempts) {
    return status(CaptchaStatus.CREATED, attempts);
  }

  private CaptchaChallenge status(CaptchaStatus status, int attempts) {
    return new CaptchaChallenge(
        ID,
        status == CaptchaStatus.CONSUMED ? null : HASH,
        status,
        attempts,
        5,
        CLOCK.instant(),
        CLOCK.instant().plusSeconds(300),
        null,
        status == CaptchaStatus.CONSUMED ? CLOCK.instant() : null,
        null,
        null);
  }

  private static final class FakeOperations implements RedisCaptchaChallengeOperations {
    String value;
    Duration ttl;
    RuntimeException failure;
    final RedisCaptchaChallengeJsonMapper json;

    FakeOperations(CaptchaChallenge c, Duration ttl, RedisCaptchaChallengeJsonMapper json) {
      this.json = json;
      this.value = json.toJson(c);
      this.ttl = ttl;
    }

    void replace(CaptchaChallenge c) {
      value = json.toJson(c);
    }

    public Mono<Boolean> setIfAbsent(String k, String v, Duration t) {
      return Mono.just(false);
    }

    public Mono<Void> set(String k, String v, Duration t) {
      value = v;
      ttl = t;
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
      if (failure != null) return Mono.error(failure);
      if (value == null) return Mono.just(AtomicReplaceResult.MISSING);
      if (!value.equals(expected)) return Mono.just(AtomicReplaceResult.CONFLICT);
      value = replacement;
      return Mono.just(AtomicReplaceResult.APPLIED);
    }
  }
}
