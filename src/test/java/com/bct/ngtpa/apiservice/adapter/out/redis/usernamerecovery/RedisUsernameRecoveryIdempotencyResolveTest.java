package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.Status;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Group 4: resolve behavior for the Username Recovery idempotency persistence adapter.
 *
 * <p>Verifies resolve distinguishes an absent record ({@code Optional.empty()}) from
 * infrastructure and serialization failures (sanitized {@link CacheException}); the
 * resolved record is never replaced by absence on failure.
 */
class RedisUsernameRecoveryIdempotencyResolveTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String KEY_VALUE = "idem_12345678";
    private static final String KEY =
            "ngtpa:username-recovery:idempotency:username-recovery-request:idem_12345678";
    private static final String OP_REF = "op-1234567890";

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> values =
            mock(ReactiveValueOperations.class);
    private final RedisUsernameRecoveryIdempotencyJsonMapper jsonMapper =
            new RedisUsernameRecoveryIdempotencyJsonMapper();
    private final RedisUsernameRecoveryIdempotencyAdapter adapter =
            new RedisUsernameRecoveryIdempotencyAdapter(
                    redis, new RedisCacheKeyFactory("ngtpa"), jsonMapper);

    @BeforeEach
    void mockValueOperations() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void resolveReturnsEmptyForAbsentRecord() {
        when(values.get(KEY)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.resolve(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey(KEY_VALUE)))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void resolveReturnsCompletedRecordWithReplaySnapshot() {
        var completed = new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                Status.COMPLETED,
                new ReplaySnapshot("snap-opaque-value"),
                NOW,
                NOW,
                NOW.plus(Duration.ofMinutes(5)));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(completed)));

        StepVerifier.create(adapter.resolve(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey(KEY_VALUE)))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get().status()).isEqualTo(Status.COMPLETED);
                    assertThat(result.get().replaySnapshot().value()).isEqualTo("snap-opaque-value");
                })
                .verifyComplete();
    }

    @Test
    void resolveReturnsUnknownOutcomeRecordWithoutReplaySnapshot() {
        var unknown = new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                Status.UNKNOWN_OUTCOME,
                null,
                NOW,
                NOW,
                NOW.plus(Duration.ofMinutes(5)));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(unknown)));

        StepVerifier.create(adapter.resolve(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey(KEY_VALUE)))
                .assertNext(result -> {
                    assertThat(result).isPresent();
                    assertThat(result.get().status()).isEqualTo(Status.UNKNOWN_OUTCOME);
                    assertThat(result.get().replaySnapshot()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void resolvePropagatesMalformedJsonAsSafeCacheFailureNotAbsence() {
        when(values.get(KEY)).thenReturn(Mono.just("{not-json"));

        StepVerifier.create(adapter.resolve(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey(KEY_VALUE)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency state deserialization failed")
                        .hasMessageNotContaining("{not-json"))
                .verify();
    }

    @Test
    void resolvePropagatesRedisFailureAsSafeCacheFailureNotAbsence() {
        when(values.get(KEY))
                .thenReturn(Mono.error(new RuntimeException("sensitive redis detail")));

        StepVerifier.create(adapter.resolve(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey(KEY_VALUE)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency resolve failed")
                        .hasMessageNotContaining("sensitive redis detail"))
                .verify();
    }
}
