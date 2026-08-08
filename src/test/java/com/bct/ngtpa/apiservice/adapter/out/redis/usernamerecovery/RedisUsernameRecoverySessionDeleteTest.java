package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Group 4: idempotent delete.
 *
 * <p>Verifies delete returns {@code DELETED}, {@code ALREADY_ABSENT}, or
 * {@code SYSTEM_FAILURE} deterministically and never silently succeeds.
 */
class RedisUsernameRecoverySessionDeleteTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private static final String KEY = "ngtpa:username-recovery:session:urs_opaque-1";

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RedisUsernameRecoverySessionAdapter adapter = new RedisUsernameRecoverySessionAdapter(
            redis, new RedisCacheKeyFactory("ngtpa"), new RedisUsernameRecoverySessionJsonMapper(), CLOCK);

    @Test
    void deleteReturnsDeletedWhenKeyPresent() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(1L));

        StepVerifier.create(adapter.delete(new UsernameRecoverySessionId("urs_opaque-1")))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.DELETED))
                .verifyComplete();

        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), eq(List.of()));
    }

    @Test
    void deleteReturnsAlreadyAbsentWhenKeyMissing() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(-1L));

        StepVerifier.create(adapter.delete(new UsernameRecoverySessionId("urs_opaque-1")))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.ALREADY_ABSENT))
                .verifyComplete();
    }

    @Test
    void deletePropagatesRedisFailureAsSafeCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("redis unavailable")));

        StepVerifier.create(adapter.delete(new UsernameRecoverySessionId("urs_opaque-1")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(com.bct.ngtpa.apiservice.exception.CacheException.class)
                        .hasMessage("Username recovery session delete failed")
                        .hasMessageNotContaining("redis unavailable"))
                .verify();
    }
}
