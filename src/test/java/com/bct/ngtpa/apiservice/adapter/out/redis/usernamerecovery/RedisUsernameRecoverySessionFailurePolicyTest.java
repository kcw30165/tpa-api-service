package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** Pins the distinction between deterministic script outcomes and infrastructure failures. */
class RedisUsernameRecoverySessionFailurePolicyTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RedisUsernameRecoverySessionAdapter adapter =
            new RedisUsernameRecoverySessionAdapter(
                    redis,
                    new RedisCacheKeyFactory("ngtpa"),
                    new RedisUsernameRecoverySessionJsonMapper(),
                    CLOCK);

    @Test
    void emptyDeleteScriptResultRemainsTypedSystemFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.empty());

        StepVerifier.create(adapter.delete(new UsernameRecoverySessionId("urs_opaque-1")))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE))
                .verifyComplete();
    }

    @Test
    void redisExceptionIsNotCollapsedIntoTypedSystemFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("sensitive redis detail")));

        StepVerifier.create(adapter.delete(new UsernameRecoverySessionId("urs_opaque-1")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery session delete failed")
                        .hasMessageNotContaining("sensitive redis detail"))
                .verify();
    }
}
