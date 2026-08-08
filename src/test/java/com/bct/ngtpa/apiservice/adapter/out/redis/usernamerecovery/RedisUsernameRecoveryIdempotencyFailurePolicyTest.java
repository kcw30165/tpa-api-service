package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort.CompleteCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort.ReserveCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Group 4: infrastructure failure policy for the Username Recovery idempotency
 * persistence adapter.
 *
 * <p>Pins the distinction between deterministic outcomes and infrastructure failures:
 * Redis, serialization, and script errors surface as sanitized {@link CacheException}
 * and are never collapsed into reservation, replay, absence, or success.
 */
class RedisUsernameRecoveryIdempotencyFailurePolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
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
    void reserveRedisFailureIsSanitizedAndNeverAReservation() {
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL)))
                .thenReturn(Mono.error(new RuntimeException("sensitive redis detail")));

        StepVerifier.create(adapter.reserve(reserveCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency reserve failed")
                        .hasMessageNotContaining("sensitive redis detail"))
                .verify();
    }

    @Test
    void reserveClassificationRedisFailureIsSanitizedAndNeverAnOutcome() {
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY))
                .thenReturn(Mono.error(new RuntimeException("sensitive redis detail")));

        StepVerifier.create(adapter.reserve(reserveCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency reserve failed")
                        .hasMessageNotContaining("sensitive redis detail"))
                .verify();
    }

    @Test
    void completeScriptFailureIsSanitizedAndNeverSuccess() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("sensitive script detail")));

        StepVerifier.create(adapter.complete(completeCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency complete failed")
                        .hasMessageNotContaining("sensitive script detail"))
                .verify();
    }

    @Test
    void unknownOutcomeScriptFailureIsSanitizedAndNeverSuccess() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("sensitive script detail")));

        StepVerifier.create(adapter.recordUnknownOutcome(unknownCommand()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency unknown outcome failed")
                        .hasMessageNotContaining("sensitive script detail"))
                .verify();
    }

    @Test
    void resolveRedisFailureIsSanitizedAndNeverAbsence() {
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

    private static ReserveCommand reserveCommand() {
        return new ReserveCommand(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                NOW,
                TTL);
    }

    private static CompleteCommand completeCommand() {
        return new CompleteCommand(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                new ReplaySnapshot("snap-opaque-value"),
                NOW,
                TTL);
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
}
