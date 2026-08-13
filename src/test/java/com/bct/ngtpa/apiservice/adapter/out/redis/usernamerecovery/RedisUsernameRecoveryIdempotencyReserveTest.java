package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort.ReserveCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.Status;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Group 2: atomic reserve matrix and expiry for the Username Recovery idempotency
 * persistence adapter.
 *
 * <p>Verifies: absent record becomes {@code RESERVED} for exactly one owner with the
 * opaque operation reference persisted; same key + same fingerprint maps by stored
 * state; same key + different fingerprint returns key-reused conflict for every stored
 * state including UNKNOWN_OUTCOME (Task 014 contract); a vanished record between
 * operations is a persistence failure, never absence; TTL comes from the command.
 */
class RedisUsernameRecoveryIdempotencyReserveTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String OTHER_DIGEST =
            "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
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
    void absentRecordBecomesReservedForExactlyOneOwnerAndPersistsOperationReference() {
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.reserve(reserveCommand(DIGEST)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoveryIdempotencyOutcome.RESERVED);
                    assertThat(result.reservation()).isNotNull();
                    assertThat(result.reservation().action())
                            .isEqualTo(UsernameRecoveryIdempotencyAction.REQUEST);
                    assertThat(result.reservation().idempotencyKey().value()).isEqualTo(KEY_VALUE);
                    assertThat(result.reservation().fingerprint().value()).isEqualTo(DIGEST);
                    assertThat(result.reservation().operationReference().value()).isEqualTo(OP_REF);
                    assertThat(result.reservation().expiresAt()).isEqualTo(NOW.plus(TTL));
                })
                .verifyComplete();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(KEY), jsonCaptor.capture(), eq(TTL));
        var persisted = jsonMapper.fromJson(jsonCaptor.getValue());
        assertThat(persisted.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(persisted.operationReference().value()).isEqualTo(OP_REF);
        assertThat(persisted.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void sameKeySameFingerprintInProgressMapsToDuplicateInProgress() {
        var existing = storedRecord(Status.IN_PROGRESS, DIGEST);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(existing)));

        assertReserveOutcome(reserveCommand(DIGEST),
                UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS);
    }

    @Test
    void sameKeyDifferentFingerprintInProgressMapsToKeyReusedConflict() {
        var existing = storedRecord(Status.IN_PROGRESS, DIGEST);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(existing)));

        assertReserveOutcome(reserveCommand(OTHER_DIGEST),
                UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST);
    }

    @Test
    void sameKeySameFingerprintCompletedMapsToCompletedReplay() {
        var existing = storedRecord(Status.COMPLETED, DIGEST);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(existing)));

        assertReserveOutcome(reserveCommand(DIGEST),
                UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY);
    }

    @Test
    void sameKeyDifferentFingerprintCompletedMapsToKeyReusedConflict() {
        var existing = storedRecord(Status.COMPLETED, DIGEST);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(existing)));

        assertReserveOutcome(reserveCommand(OTHER_DIGEST),
                UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST);
    }

    @Test
    void sameKeySameFingerprintUnknownOutcomeMapsToUnknownOutcomeExisting() {
        var existing = storedRecord(Status.UNKNOWN_OUTCOME, DIGEST);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(existing)));

        assertReserveOutcome(reserveCommand(DIGEST),
                UsernameRecoveryIdempotencyOutcome.UNKNOWN_OUTCOME_EXISTING);
    }

    @Test
    void sameKeyDifferentFingerprintUnknownOutcomeMapsToKeyReusedConflict() {
        // Task 014 contract: different fingerprint is a key-reused conflict even when
        // the stored state is a stable unknown outcome.
        var existing = storedRecord(Status.UNKNOWN_OUTCOME, DIGEST);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(existing)));

        assertReserveOutcome(reserveCommand(OTHER_DIGEST),
                UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST);
    }

    @Test
    void nonReservedOutcomesNeverCarryAReservation() {
        var existing = storedRecord(Status.COMPLETED, DIGEST);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(existing)));

        StepVerifier.create(adapter.reserve(reserveCommand(DIGEST)))
                .assertNext(result -> {
                    assertThat(result.outcome())
                            .isEqualTo(UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY);
                    assertThat(result.reservation()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void vanishedRecordBetweenOperationsIsPersistenceFailureNeverAbsence() {
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(Mono.just(false));
        when(values.get(KEY)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.reserve(reserveCommand(DIGEST)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery idempotency reserve failed"))
                .verify();
    }

    @Test
    void ttlIsPositiveAndDerivedFromCommandExpiryContract() {
        assertThat(TTL.isPositive()).isTrue();
        assertThat(reserveCommand(DIGEST).expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    private void assertReserveOutcome(
            ReserveCommand command, UsernameRecoveryIdempotencyOutcome expected) {
        StepVerifier.create(adapter.reserve(command))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(expected);
                    assertThat(result.reservation()).isNull();
                })
                .verifyComplete();
    }

    private static ReserveCommand reserveCommand(String fingerprint) {
        return new ReserveCommand(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(fingerprint),
                new UsernameRecoveryIdempotencyOperationReference(OP_REF),
                NOW,
                TTL);
    }

    private static UsernameRecoveryIdempotencyRecord storedRecord(Status status, String fingerprint) {
        return new UsernameRecoveryIdempotencyRecord(
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey(KEY_VALUE),
                new UsernameRecoveryIdempotencyFingerprint(fingerprint),
                new UsernameRecoveryIdempotencyOperationReference("op-existing-1"),
                status,
                status == Status.COMPLETED
                        ? new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("snap-existing-1")
                        : null,
                NOW,
                NOW,
                NOW.plus(TTL));
    }
}
