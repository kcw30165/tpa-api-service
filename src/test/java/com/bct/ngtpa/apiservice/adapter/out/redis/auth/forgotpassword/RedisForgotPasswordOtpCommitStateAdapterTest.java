package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link RedisForgotPasswordOtpCommitStateAdapter}. */
@SuppressWarnings("unchecked")
class RedisForgotPasswordOtpCommitStateAdapterTest {

  private final RedisForgotPasswordOtpCommitStateKeyFactory keys =
      new RedisForgotPasswordOtpCommitStateKeyFactory("ngtpa");
  private final RedisForgotPasswordOtpCommitStateJsonMapper mapper =
      new RedisForgotPasswordOtpCommitStateJsonMapper();
  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values =
      mock(ReactiveValueOperations.class);

  private RedisForgotPasswordOtpCommitStateAdapter newAdapter() {
    return new RedisForgotPasswordOtpCommitStateAdapter(keys, mapper, redis);
  }

  private static final String REF =
      "0123456789012345678901"; // 22 chars, matches key factory's regex

  private ForgotPasswordOtpCommitState state() {
    return new ForgotPasswordOtpCommitState(
        "afs-1",
        "challenge-1",
        Instant.parse("2026-09-14T00:00:00Z"),
        Instant.parse("2026-09-14T00:10:00Z"));
  }

  // ----- saveIfAbsent(): guards -------------------------------------------

  @Test
  void saveIfAbsentNullTtlErrors() {
    StepVerifier.create(newAdapter().saveIfAbsent(REF, state(), null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void saveIfAbsentZeroTtlErrors() {
    StepVerifier.create(newAdapter().saveIfAbsent(REF, state(), Duration.ZERO))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  // ----- saveIfAbsent(): success / contention ---------------------------------

  @Test
  void saveIfAbsentSucceedsWhenTrue() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
    StepVerifier.create(newAdapter().saveIfAbsent(REF, state(), Duration.ofMinutes(5)))
        .expectNext(true)
        .verifyComplete();
  }

  @Test
  void saveIfAbsentYieldsFalseWhenAlreadyPresent() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    StepVerifier.create(newAdapter().saveIfAbsent(REF, state(), Duration.ofMinutes(5)))
        .expectNext(false)
        .verifyComplete();
  }

  // ----- load() ---------------------------------------------------------------

  @Test
  void loadMissingYieldsEmpty() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().load(REF))
        .expectNext(java.util.Optional.empty())
        .verifyComplete();
  }

  @Test
  void loadFoundReturnsState() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(any())).thenReturn(Mono.just(mapper.toJson(state())));
    StepVerifier.create(newAdapter().load(REF))
        .assertNext(optional -> assertThat(optional).isPresent())
        .verifyComplete();
  }

  // ----- delete() ---------------------------------------------------------------

  @Test
  void deleteCompletes() {
    when(redis.delete((String) any())).thenReturn(Mono.just(1L));
    StepVerifier.create(newAdapter().delete(REF)).verifyComplete();
  }
}
