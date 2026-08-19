package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisAuthFlowSessionCompletionLockAdapterTest {
  @Test
  void acquireUsesOpaqueSessionScopedKeyAndLease() {
    ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(
            eq("ngtpa:auth:completion-lock:afs_safe"), anyString(), eq(Duration.ofSeconds(30))))
        .thenReturn(Mono.just(true));
    var adapter =
        new RedisAuthFlowSessionCompletionLockAdapter(redis, new RedisCacheKeyFactory("ngtpa"));
    StepVerifier.create(adapter.acquire(new AuthFlowSessionId("afs_safe"), Duration.ofSeconds(30)))
        .assertNext(
            h -> {
              assertThat(h.acquired()).isTrue();
              assertThat(h.toString()).doesNotContain(h.lockKey(), h.ownershipToken());
            })
        .verifyComplete();
  }

  @Test
  void busyLockReturnsNotAcquired() {
    ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .thenReturn(Mono.just(false));
    StepVerifier.create(
            new RedisAuthFlowSessionCompletionLockAdapter(redis, new RedisCacheKeyFactory("ngtpa"))
                .acquire(new AuthFlowSessionId("afs_safe"), Duration.ofSeconds(30)))
        .assertNext(h -> assertThat(h.acquired()).isFalse())
        .verifyComplete();
  }

  @Test
  void releaseIsOwnershipCheckedByAtomicScript() {
    ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    when(redis.execute(any(), anyList(), anyString())).thenReturn(Flux.just(1L));
    var adapter =
        new RedisAuthFlowSessionCompletionLockAdapter(redis, new RedisCacheKeyFactory("ngtpa"));
    StepVerifier.create(
            adapter.release(
                new SessionLockHandle(true, "ngtpa:auth:completion-lock:afs_safe", "owner")))
        .expectNext(true)
        .verifyComplete();
  }
}
