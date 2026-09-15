package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ProtectedForgotPasswordStartReplay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayState;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisForgotPasswordStartProtectedReplayAdapter}.
 * Mirrors the proven Redis store/load-with-collision-retry pattern (rounds 28-33, 41-43, 50-58).
 */
@SuppressWarnings("unchecked")
class RedisForgotPasswordStartProtectedReplayAdapterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
  private final RedisForgotPasswordStartProtectedReplayKeyFactory keys =
      mock(RedisForgotPasswordStartProtectedReplayKeyFactory.class);
  private final RedisForgotPasswordStartProtectedReplayJsonMapper json =
      mock(RedisForgotPasswordStartProtectedReplayJsonMapper.class);
  private final ForgotPasswordStartProtectedReplayProtectorPort protector =
      mock(ForgotPasswordStartProtectedReplayProtectorPort.class);
  private final ForgotPasswordStartProtectedReplayReferenceGenerator references =
      mock(ForgotPasswordStartProtectedReplayReferenceGenerator.class);

  private final ProtectedReplayReference reference =
      new ProtectedReplayReference("replay-ref-1234567890abcd");

  private RedisForgotPasswordStartProtectedReplayAdapter newAdapter() {
    return new RedisForgotPasswordStartProtectedReplayAdapter(redis, keys, json, protector, references);
  }

  private void givenRedisReady() {
    when(redis.opsForValue()).thenReturn(values);
    when(keys.keyFor(any())).thenReturn("redis-key-1");
  }

  // ----- store() ---------------------------------------------------------------

  @Test
  void storeWithNullTtlErrors() {
    StepVerifier.create(newAdapter().store(mock(ForgotPasswordStartProtectedReplayState.class), null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void storeWithZeroTtlErrors() {
    StepVerifier.create(
            newAdapter().store(mock(ForgotPasswordStartProtectedReplayState.class), Duration.ZERO))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void storeSucceedsOnFirstAttempt() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedForgotPasswordStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
    StepVerifier.create(
            newAdapter()
                .store(mock(ForgotPasswordStartProtectedReplayState.class), Duration.ofMinutes(5)))
        .assertNext(r -> assertThat(r).isEqualTo(reference))
        .verifyComplete();
  }

  @Test
  void storeRetriesOnCollisionThenSucceeds() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedForgotPasswordStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false)).thenReturn(Mono.just(true));
    StepVerifier.create(
            newAdapter()
                .store(mock(ForgotPasswordStartProtectedReplayState.class), Duration.ofMinutes(5)))
        .assertNext(r -> assertThat(r).isEqualTo(reference))
        .verifyComplete();
  }

  @Test
  void storeExhaustsCollisionAttemptsAndErrors() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedForgotPasswordStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    StepVerifier.create(
            newAdapter()
                .store(mock(ForgotPasswordStartProtectedReplayState.class), Duration.ofMinutes(5)))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- load() ---------------------------------------------------------------

  @Test
  void loadMissingKeyErrors() {
    givenRedisReady();
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().load(reference))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void loadSucceeds() {
    givenRedisReady();
    when(values.get(any())).thenReturn(Mono.just("{}"));
    ProtectedForgotPasswordStartReplay protectedValue = mock(ProtectedForgotPasswordStartReplay.class);
    when(json.fromJson(any())).thenReturn(protectedValue);
    ForgotPasswordStartProtectedReplayState state = mock(ForgotPasswordStartProtectedReplayState.class);
    when(protector.unprotect(any(), any())).thenReturn(state);
    StepVerifier.create(newAdapter().load(reference)).expectNext(state).verifyComplete();
  }
}
