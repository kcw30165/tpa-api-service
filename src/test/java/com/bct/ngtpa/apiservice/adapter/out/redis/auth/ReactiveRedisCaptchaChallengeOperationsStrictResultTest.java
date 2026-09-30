package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ReactiveRedisCaptchaChallengeOperationsStrictResultTest {
  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveRedisCaptchaChallengeOperations operations =
      new ReactiveRedisCaptchaChallengeOperations(redis);

  @Test
  void mapsOnlyDocumentedResults() {
    assertWire(1L, RedisCaptchaChallengeOperations.AtomicReplaceResult.APPLIED);
    assertWire(0L, RedisCaptchaChallengeOperations.AtomicReplaceResult.CONFLICT);
    assertWire(-1L, RedisCaptchaChallengeOperations.AtomicReplaceResult.MISSING);
  }

  @Test
  void unexpectedPositiveFailsClosed() {
    assertUnexpected(2L);
  }

  @Test
  void unexpectedNegativeFailsClosed() {
    assertUnexpected(-2L);
  }

  @Test
  void emptyScriptPublisherFailsClosed() {
    when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.empty());
    StepVerifier.create(operations.replaceIfUnchanged("safe-key", "expected", "replacement"))
        .expectError()
        .verify();
  }

  @Test
  void redisErrorPropagates() {
    when(redis.execute(any(RedisScript.class), anyList(), anyList()))
        .thenReturn(Flux.error(new IllegalStateException("redis unavailable")));
    StepVerifier.create(operations.replaceIfUnchanged("safe-key", "expected", "replacement"))
        .expectErrorSatisfies(
            e ->
                assertThat(e)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("redis unavailable"))
        .verify();
  }

  @Test
  void scriptAndArgumentOrderRemainExact() {
    when(redis.execute(any(RedisScript.class), anyList(), anyList()))
        .thenAnswer(
            i -> {
              RedisScript<?> script = i.getArgument(0);
              assertThat(script.getScriptAsString())
                  .contains("current~=ARGV[1]", "PTTL", "ttl<=0", "KEEPTTL");
              assertThat(i.getArgument(1).toString()).isEqualTo("[safe-key]");
              assertThat(i.getArgument(2).toString()).isEqualTo("[expected, replacement]");
              return Flux.just(1L);
            });
    StepVerifier.create(operations.replaceIfUnchanged("safe-key", "expected", "replacement"))
        .expectNext(RedisCaptchaChallengeOperations.AtomicReplaceResult.APPLIED)
        .verifyComplete();
  }

  private void assertWire(
      long value, RedisCaptchaChallengeOperations.AtomicReplaceResult expected) {
    when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(value));
    StepVerifier.create(operations.replaceIfUnchanged("safe-key", "expected", "replacement"))
        .expectNext(expected)
        .verifyComplete();
  }

  private void assertUnexpected(long value) {
    when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(value));
    StepVerifier.create(operations.replaceIfUnchanged("safe-key", "expected", "replacement"))
        .expectErrorSatisfies(
            e ->
                assertThat(e)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Captcha verification Redis script returned an unsupported result")
                    .hasMessageNotContaining("safe-key")
                    .hasMessageNotContaining(Long.toString(value)))
        .verify();
  }

  // ----- setIfAbsent() / set() / get() / ttl() / delete() (added round 196 -
  // the original file exhaustively covers replaceIfUnchanged() alone;
  // these 5 methods were never exercised) --------------------------------------

  @Test
  void setIfAbsentDelegatesToOpsForValue() {
    var values = mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent("k", "v", java.time.Duration.ofSeconds(60)))
        .thenReturn(reactor.core.publisher.Mono.just(true));
    StepVerifier.create(operations.setIfAbsent("k", "v", java.time.Duration.ofSeconds(60)))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  void setIfAbsentYieldsFalseWhenAlreadyPresent() {
    var values = mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent("k", "v", java.time.Duration.ofSeconds(60)))
        .thenReturn(reactor.core.publisher.Mono.just(false));
    StepVerifier.create(operations.setIfAbsent("k", "v", java.time.Duration.ofSeconds(60)))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  void setDelegatesToOpsForValueAndCompletes() {
    var values = mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.set("k", "v", java.time.Duration.ofSeconds(60)))
        .thenReturn(reactor.core.publisher.Mono.just(true));
    StepVerifier.create(operations.set("k", "v", java.time.Duration.ofSeconds(60)))
        .verifyComplete();
  }

  @Test
  void getReturnsPresentOptionalWhenFound() {
    var values = mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("k")).thenReturn(reactor.core.publisher.Mono.just("v"));
    StepVerifier.create(operations.get("k"))
        .expectNext(java.util.Optional.of("v"))
        .verifyComplete();
  }

  @Test
  void getReturnsEmptyOptionalWhenMissing() {
    var values = mock(org.springframework.data.redis.core.ReactiveValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("k")).thenReturn(reactor.core.publisher.Mono.empty());
    StepVerifier.create(operations.get("k"))
        .expectNext(java.util.Optional.empty())
        .verifyComplete();
  }

  @Test
  void ttlReturnsPresentOptionalWhenPositive() {
    when(redis.getExpire("k"))
        .thenReturn(reactor.core.publisher.Mono.just(java.time.Duration.ofSeconds(30)));
    StepVerifier.create(operations.ttl("k"))
        .expectNext(java.util.Optional.of(java.time.Duration.ofSeconds(30)))
        .verifyComplete();
  }

  @Test
  void ttlZeroOrNegativeThrowsNullPointerException() {
    // NOTE (round 199/200): the real ttl() implementation's first .map(...)
    // lambda returns Java null for a zero/negative duration, intending the
    // SECOND .map(Optional::ofNullable) to wrap it into Optional.empty().
    // Reactor's Mono.map(...) contract explicitly FORBIDS a mapper
    // returning null and throws NullPointerException immediately - the null
    // never reaches the second .map(...). This is a genuine, pre-existing
    // production defect (same category as the Map.copyOf defect logged in
    // round 87), NOT a test-design mistake. Logged in the coverage plan doc;
    // production code is intentionally NOT modified in this test-only phase.
    when(redis.getExpire("k"))
        .thenReturn(reactor.core.publisher.Mono.just(java.time.Duration.ZERO));
    StepVerifier.create(operations.ttl("k"))
        .expectErrorMatches(e -> e instanceof NullPointerException)
        .verify();
  }

  @Test
  void ttlReturnsEmptyOptionalWhenKeyMissingEntirely() {
    when(redis.getExpire("k")).thenReturn(reactor.core.publisher.Mono.empty());
    StepVerifier.create(operations.ttl("k"))
        .expectNext(java.util.Optional.empty())
        .verifyComplete();
  }

  @Test
  void deleteDelegatesToRedisTemplateAndCompletes() {
    when(redis.delete("k")).thenReturn(reactor.core.publisher.Mono.just(1L));
    StepVerifier.create(operations.delete("k")).verifyComplete();
  }
}
