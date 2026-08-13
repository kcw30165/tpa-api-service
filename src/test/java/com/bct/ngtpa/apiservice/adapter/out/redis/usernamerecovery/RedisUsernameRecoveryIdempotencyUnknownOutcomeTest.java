package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.Status;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Group 3: stable unknown-outcome recording for the Username Recovery idempotency
 * persistence adapter.
 *
 * <p>Verifies the unknown-outcome transition is ownership-checked like completion,
 * stores NO replay snapshot, and every non-success Lua outcome becomes a sanitized
 * {@link CacheException} (the record is never blindly resent).
 */
class RedisUsernameRecoveryIdempotencyUnknownOutcomeTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String KEY_VALUE = "idem_12345678";
    private static final String KEY =
            "ngtpa:username-recovery:idempotency:username-recovery-request:idem_12345678";
    private static final String OP_REF = "op-1234567890";

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RedisUsernameRecoveryIdempotencyJsonMapper jsonMapper =
            new RedisUsernameRecoveryIdempotencyJsonMapper();
    private final RedisUsernameRecoveryIdempotencyAdapter adapter =
            new RedisUsernameRecoveryIdempotencyAdapter(
                    redis, new RedisCacheKeyFactory("ngtpa"), jsonMapper);

    @Test
    void recordUnknownOutcomeStoresNoReplaySnapshotAndReturnsUnknownRecord() {
        var unknown = unknownRecord();
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(jsonMapper.toJson(unknown)));

        StepVerifier.create(adapter.recordUnknownOutcome(unknownCommand()))
                .assertNext(record -> {
                    assertThat(record.status()).isEqualTo(Status.UNKNOWN_OUTCOME);
                    assertThat(record.replaySnapshot()).isNull();
                    assertThat(record.expiresAt()).isEqualTo(NOW.plus(TTL));
                })
                .verifyComplete();

        verify(redis).execute(
                any(RedisScript.class),
                eq(List.of(KEY)),
                eq(List.of("REQUEST", KEY_VALUE, DIGEST, OP_REF,
                        "UNKNOWN_OUTCOME", NOW.toString(), "")));
    }

    @Test
    void unknownOutcomeMapsMissingScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("MISSING"));

        assertUnknownOutcomeFailsWithSafeMessage();
    }

    @Test
    void unknownOutcomeMapsConflictScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("CONFLICT"));

        assertUnknownOutcomeFailsWithSafeMessage();
    }

    @Test
    void unknownOutcomeMapsExpiredScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("EXPIRED"));

        assertUnknownOutcomeFailsWithSafeMessage();
    }

    @Test
    void unknownOutcomeMapsCorruptScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("CORRUPT"));

        assertUnknownOutcomeFailsWithSafeMessage();
    }

    @Test
    void unknownOutcomeMapsEmptyScriptResultToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.empty());

        assertUnknownOutcomeFailsWithSafeMessage();
    }

    @Test
    void unknownOutcomeFailureMessageNeverDisclosesOwnershipValues() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("CONFLICT"));

        StepVerifier.create(adapter.recordUnknownOutcome(unknownCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency unknown outcome failed")
                        .hasMessageNotContaining(KEY_VALUE)
                        .hasMessageNotContaining(DIGEST)
                        .hasMessageNotContaining(OP_REF))
                .verify();
    }

    private void assertUnknownOutcomeFailsWithSafeMessage() {
        StepVerifier.create(adapter.recordUnknownOutcome(unknownCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency unknown outcome failed"))
                .verify();
    }

    private static UnknownOutcomeCommand unknownCommand() {
        return new UnknownOutcomeCommand(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                NOW,
                TTL);
    }

    private static UsernameRecoveryIdempotencyRecord unknownRecord() {
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                Status.UNKNOWN_OUTCOME,
                null,
                NOW,
                NOW,
                NOW.plus(TTL));
    }
}
