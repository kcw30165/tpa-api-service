package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisAuthFlowSessionMutationLockAdapter}. Mirrors the
 * proven ReactiveStringRedisTemplate mocking pattern used throughout this codebase. Both
 * constructor dependencies (ReactiveStringRedisTemplate, RedisCacheKeyFactory) are already fully
 * confirmed types - no guessing.
 */
@SuppressWarnings("unchecked")
class RedisAuthFlowSessionMutationLockAdapterTest {

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values =
      mock(ReactiveValueOperations.class);
  private final RedisCacheKeyFactory keys = mock(RedisCacheKeyFactory.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-lock-1");

  private RedisAuthFlowSessionMutationLockAdapter newAdapter() {
    return new RedisAuthFlowSessionMutationLockAdapter(redis, keys);
  }

  private void givenKeyFactoryReady() {
    when(keys.key("auth", "mutation-lock", sid.value())).thenReturn("redis-key-1");
  }

  // ----- acquire(): guards --------------------------------------------------------

  @Test
  void acquireWithNullSessionIdThrows() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> newAdapter().acquire(null, Duration.ofSeconds(30)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void acquireWithNullLeaseYieldsIllegalArgument() {
    StepVerifier.create(newAdapter().acquire(sid, null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void acquireWithZeroLeaseYieldsIllegalArgument() {
    StepVerifier.create(newAdapter().acquire(sid, Duration.ZERO))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void acquireWithNegativeLeaseYieldsIllegalArgument() {
    StepVerifier.create(newAdapter().acquire(sid, Duration.ofSeconds(-1)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  // ----- acquire(): success / contention / error -------------------------------------

  @Test
  void acquireSucceedsWhenSetIfAbsentReturnsTrue() {
    givenKeyFactoryReady();
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(eq("redis-key-1"), any(), eq(Duration.ofSeconds(30))))
        .thenReturn(Mono.just(true));
    StepVerifier.create(newAdapter().acquire(sid, Duration.ofSeconds(30)))
        .assertNext(
            handle -> {
              assertThat(handle.acquired()).isTrue();
              assertThat(handle.lockKey()).isEqualTo("redis-key-1");
              assertThat(handle.ownershipToken()).isNotBlank();
            })
        .verifyComplete();
  }

  @Test
  void acquireYieldsNotAcquiredWhenSetIfAbsentReturnsFalse() {
    givenKeyFactoryReady();
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    StepVerifier.create(newAdapter().acquire(sid, Duration.ofSeconds(30)))
        .assertNext(handle -> assertThat(handle.acquired()).isFalse())
        .verifyComplete();
  }

  @Test
  void acquireRedisErrorYieldsCacheException() {
    givenKeyFactoryReady();
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().acquire(sid, Duration.ofSeconds(30)))
        .expectError(CacheException.class)
        .verify();
  }

  // ----- release(): guards --------------------------------------------------------

  @Test
  void releaseWithNullHandleThrows() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> newAdapter().release(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void releaseNotAcquiredHandleSkipsRedisAndReturnsFalse() {
    StepVerifier.create(newAdapter().release(SessionLockHandle.notAcquired()))
        .expectNext(false)
        .verifyComplete();
    verify(redis, never())
        .execute(any(DefaultRedisScript.class), any(List.class), new Object[] {any()});
  }

  // ----- release(): success / mismatch / error -------------------------------------

  @Test
  void releaseSucceedsWhenScriptReturnsPositive() {
    SessionLockHandle handle = new SessionLockHandle(true, "redis-key-1", "token-1");
    when(redis.execute(
            any(DefaultRedisScript.class),
            eq(List.of("redis-key-1")),
            new Object[] {eq("token-1")}))
        .thenReturn(Flux.just(1L));
    StepVerifier.create(newAdapter().release(handle)).expectNext(true).verifyComplete();
  }

  @Test
  void releaseYieldsFalseWhenScriptReturnsZero() {
    SessionLockHandle handle = new SessionLockHandle(true, "redis-key-1", "token-1");
    when(redis.execute(any(DefaultRedisScript.class), any(List.class), new Object[] {any()}))
        .thenReturn(Flux.just(0L));
    StepVerifier.create(newAdapter().release(handle)).expectNext(false).verifyComplete();
  }

  @Test
  void releaseYieldsFalseWhenScriptReturnsEmpty() {
    SessionLockHandle handle = new SessionLockHandle(true, "redis-key-1", "token-1");
    when(redis.execute(any(DefaultRedisScript.class), any(List.class), new Object[] {any()}))
        .thenReturn(Flux.empty());
    StepVerifier.create(newAdapter().release(handle)).expectNext(false).verifyComplete();
  }

  @Test
  void releaseRedisErrorYieldsCacheException() {
    SessionLockHandle handle = new SessionLockHandle(true, "redis-key-1", "token-1");
    when(redis.execute(any(DefaultRedisScript.class), any(List.class), new Object[] {any()}))
        .thenReturn(Flux.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().release(handle)).expectError(CacheException.class).verify();
  }
}
