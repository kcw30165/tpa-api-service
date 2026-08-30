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
}
