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
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort.CompleteCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
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
 * Group 3: ownership-checked completion for the Username Recovery idempotency
 * persistence adapter.
 *
 * <p>Verifies the complete transition atomically verifies action, key, fingerprint,
 * and operation reference against the stored IN_PROGRESS record, stores the opaque
 * replay snapshot, preserves createdAt/expiresAt, and surfaces every non-success Lua
 * outcome as a sanitized {@link CacheException}.
 */
class RedisUsernameRecoveryIdempotencyCompleteTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String KEY_VALUE = "idem_12345678";
    private static final String KEY =
            "ngtpa:username-recovery:idempotency:username-recovery-request:idem_12345678";
    private static final String OP_REF = "op-1234567890";
    private static final String SNAPSHOT = "snap-opaque-value";

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RedisUsernameRecoveryIdempotencyJsonMapper jsonMapper =
            new RedisUsernameRecoveryIdempotencyJsonMapper();
    private final RedisUsernameRecoveryIdempotencyAdapter adapter =
            new RedisUsernameRecoveryIdempotencyAdapter(
                    redis, new RedisCacheKeyFactory("ngtpa"), jsonMapper);

    @Test
    void completeStoresReplaySnapshotAndReturnsCompletedRecord() {
        var completed = completedRecord();
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(jsonMapper.toJson(completed)));

        StepVerifier.create(adapter.complete(completeCommand()))
                .assertNext(record -> {
                    assertThat(record.status()).isEqualTo(Status.COMPLETED);
                    assertThat(record.replaySnapshot().value()).isEqualTo(SNAPSHOT);
                    assertThat(record.action()).isEqualTo(UsernameRecoveryIdempotencyAction.REQUEST);
                    assertThat(record.expiresAt()).isEqualTo(NOW.plus(TTL));
                })
                .verifyComplete();

        verify(redis).execute(
                any(RedisScript.class),
                eq(List.of(KEY)),
                eq(List.of("REQUEST", KEY_VALUE, DIGEST, OP_REF,
                        "COMPLETED", NOW.toString(), SNAPSHOT)));
    }

    @Test
    void completeMapsMissingScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("MISSING"));

        assertCompleteFailsWithSafeMessage();
    }

    @Test
    void completeMapsConflictScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("CONFLICT"));

        assertCompleteFailsWithSafeMessage();
    }

    @Test
    void completeMapsExpiredScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("EXPIRED"));

        assertCompleteFailsWithSafeMessage();
    }

    @Test
    void completeMapsCorruptScriptCodeToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("CORRUPT"));

        assertCompleteFailsWithSafeMessage();
    }

    @Test
    void completeMapsEmptyScriptResultToSanitizedCacheFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.empty());

        assertCompleteFailsWithSafeMessage();
    }

    @Test
    void completeNeverDisclosesOwnershipOrSnapshotInFailureMessage() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just("CONFLICT"));

        StepVerifier.create(adapter.complete(completeCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency complete failed")
                        .hasMessageNotContaining(KEY_VALUE)
                        .hasMessageNotContaining(DIGEST)
                        .hasMessageNotContaining(OP_REF)
                        .hasMessageNotContaining(SNAPSHOT))
                .verify();
    }

    private void assertCompleteFailsWithSafeMessage() {
        StepVerifier.create(adapter.complete(completeCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency complete failed"))
                .verify();
    }

    private static CompleteCommand completeCommand() {
        return new CompleteCommand(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                new ReplaySnapshot(SNAPSHOT),
                NOW,
                TTL);
    }

    private static UsernameRecoveryIdempotencyRecord completedRecord() {
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                Status.COMPLETED,
                new ReplaySnapshot(SNAPSHOT),
                NOW,
                NOW,
                NOW.plus(TTL));
    }
}
