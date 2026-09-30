package com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.ProtectedRegistrationStartReplay;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayState;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisRegistrationStartProtectedReplayAdapter} -
 * structurally identical to {@code RedisForgotPasswordStartProtectedReplayAdapterTest} (confirmed
 * round 82: same store()/load()/collision-retry pattern, different type names).
 */
@SuppressWarnings("unchecked")
class RedisRegistrationStartProtectedReplayAdapterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values =
      mock(ReactiveValueOperations.class);
  private final RedisRegistrationStartProtectedReplayKeyFactory keys =
      mock(RedisRegistrationStartProtectedReplayKeyFactory.class);
  private final RedisRegistrationStartProtectedReplayJsonMapper json =
      mock(RedisRegistrationStartProtectedReplayJsonMapper.class);
  private final RegistrationStartProtectedReplayProtectorPort protector =
      mock(RegistrationStartProtectedReplayProtectorPort.class);
  private final RegistrationStartProtectedReplayReferenceGenerator references =
      mock(RegistrationStartProtectedReplayReferenceGenerator.class);

  private final ProtectedReplayReference reference =
      new ProtectedReplayReference("replay-ref-abcdefghijklmn");

  private RedisRegistrationStartProtectedReplayAdapter newAdapter() {
    return new RedisRegistrationStartProtectedReplayAdapter(
        redis, keys, json, protector, references);
  }

  private void givenRedisReady() {
    when(redis.opsForValue()).thenReturn(values);
    when(keys.keyFor(any())).thenReturn("redis-key-1");
  }

  // ----- store() ---------------------------------------------------------------

  @Test
  void storeWithNullTtlErrors() {
    StepVerifier.create(newAdapter().store(mock(RegistrationStartProtectedReplayState.class), null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void storeWithZeroTtlErrors() {
    StepVerifier.create(
            newAdapter().store(mock(RegistrationStartProtectedReplayState.class), Duration.ZERO))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void storeSucceedsOnFirstAttempt() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedRegistrationStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
    StepVerifier.create(
            newAdapter()
                .store(mock(RegistrationStartProtectedReplayState.class), Duration.ofMinutes(5)))
        .assertNext(r -> assertThat(r).isEqualTo(reference))
        .verifyComplete();
  }

  @Test
  void storeRetriesOnCollisionThenSucceeds() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedRegistrationStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any()))
        .thenReturn(Mono.just(false))
        .thenReturn(Mono.just(true));
    StepVerifier.create(
            newAdapter()
                .store(mock(RegistrationStartProtectedReplayState.class), Duration.ofMinutes(5)))
        .assertNext(r -> assertThat(r).isEqualTo(reference))
        .verifyComplete();
  }

  @Test
  void storeExhaustsCollisionAttemptsAndErrors() {
    givenRedisReady();
    when(references.generate()).thenReturn(reference);
    when(protector.protect(any(), any())).thenReturn(mock(ProtectedRegistrationStartReplay.class));
    when(json.toJson(any())).thenReturn("{}");
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    StepVerifier.create(
            newAdapter()
                .store(mock(RegistrationStartProtectedReplayState.class), Duration.ofMinutes(5)))
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
    ProtectedRegistrationStartReplay protectedValue = mock(ProtectedRegistrationStartReplay.class);
    when(json.fromJson(any())).thenReturn(protectedValue);
    RegistrationStartProtectedReplayState state = mock(RegistrationStartProtectedReplayState.class);
    when(protector.unprotect(any(), any())).thenReturn(state);
    StepVerifier.create(newAdapter().load(reference)).expectNext(state).verifyComplete();
  }
}
