package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisForgotPasswordIdempotencyAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final String HASH = "0".repeat(64);
    private static final String OTHER_HASH = "1".repeat(64);

    private final InMemoryRedisOperations redis = new InMemoryRedisOperations();
    private final RedisForgotPasswordIdempotencyAdapter adapter = new RedisForgotPasswordIdempotencyAdapter(
            new RedisForgotPasswordIdempotencyKeyFactory("ngtpa"),
            new RedisForgotPasswordIdempotencyJsonMapper(),
            redis);

    @Test
    void startProcessingCreatesOpaqueProcessingRecord() {
        StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(ForgotPasswordIdempotencyStartOutcome.STARTED);
                    assertThat(result.record().status()).isEqualTo(ForgotPasswordIdempotencyStatus.PROCESSING);
                    assertThat(result.record().action()).isEqualTo(ForgotPasswordIdempotencyAction.START);
                })
                .verifyComplete();

        assertThat(redis.lastKey).isEqualTo("ngtpa:auth:idempotency:forgot-password-start:idem_fp_123");
        assertThat(redis.lastKey).doesNotContain("member01", "csrf", "auth_flow_sid");
        assertThat(redis.ttlFor(redis.lastKey)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void startProcessingClassifiesReplayAndKeyReuse() {
        adapter.startProcessing(startCommand(HASH)).block();
        adapter.markCompleted(completeCommand(HASH)).block();

        StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(ForgotPasswordIdempotencyStartOutcome.COMPLETED_REPLAY);
                    assertThat(result.record().replaySnapshot()).isNotNull();
                    assertThat(result.record().replaySnapshot().safeHeaders()).containsEntry("X-Request-Id", "req-1");
                    assertThat(result.record().replaySnapshot().identityType()).isEqualTo("STAFF");
                })
                .verifyComplete();

        StepVerifier.create(adapter.startProcessing(startCommand(OTHER_HASH)))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(ForgotPasswordIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST))
                .verifyComplete();
    }

    @Test
    void failedAndUnknownOutcomeStatesRemainReplaySafe() {
        adapter.startProcessing(startCommand(HASH)).block();
        adapter.markFailed(failCommand(HASH)).block();

        StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(ForgotPasswordIdempotencyStartOutcome.FAILED_EXISTING))
                .verifyComplete();

        redis.clear();
        adapter.startProcessing(startCommand(HASH)).block();
        adapter.markUnknownOutcome(unknownCommand(HASH)).block();

        StepVerifier.create(adapter.startProcessing(startCommand(HASH)))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(ForgotPasswordIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING))
                .verifyComplete();
    }

    @Test
    void loadRoundTripsCompletedSnapshotWithoutCookieOrCsrfHeaders() {
        adapter.startProcessing(startCommand(HASH)).block();
        adapter.markCompleted(completeCommand(HASH)).block();

        StepVerifier
                .create(adapter.load(ForgotPasswordIdempotencyAction.START,
                        new ForgotPasswordIdempotencyKey("idem_fp_123")))
                .assertNext(optional -> {
                    assertThat(optional).isPresent();
                    assertThat(optional.get().status()).isEqualTo(ForgotPasswordIdempotencyStatus.COMPLETED);
                    assertThat(optional.get().replaySnapshot().safeHeaders()).containsOnlyKeys("X-Request-Id");
                })
                .verifyComplete();
    }

    @Test
    void redisFailuresStaySanitized() {
        var failingAdapter = new RedisForgotPasswordIdempotencyAdapter(
                new RedisForgotPasswordIdempotencyKeyFactory("ngtpa"),
                new RedisForgotPasswordIdempotencyJsonMapper(),
                new FailingRedisOperations());

        StepVerifier.create(failingAdapter.startProcessing(startCommand(HASH)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Redis forgot password idempotency operation failed")
                        .hasMessageNotContaining("idem_fp_123")
                        .hasMessageNotContaining("forgot-password-start")
                        .hasMessageNotContaining(HASH))
                .verify();
    }

    private static ForgotPasswordIdempotencyStartCommand startCommand(String hash) {
        return new ForgotPasswordIdempotencyStartCommand(
                ForgotPasswordIdempotencyAction.START,
                new ForgotPasswordIdempotencyKey("idem_fp_123"),
                new ForgotPasswordRequestHash(hash),
                NOW,
                NOW.plusSeconds(300),
                Duration.ofMinutes(5));
    }

    private static ForgotPasswordIdempotencyCompleteCommand completeCommand(String hash) {
        return new ForgotPasswordIdempotencyCompleteCommand(
                ForgotPasswordIdempotencyAction.START,
                new ForgotPasswordIdempotencyKey("idem_fp_123"),
                new ForgotPasswordRequestHash(hash),
                new ForgotPasswordIdempotencyReplaySnapshot("STARTED", "afs_existing", Map.of("X-Request-Id", "req-1"), "STAFF"),
                NOW.plusSeconds(10),
                Duration.ofMinutes(10));
    }

    private static ForgotPasswordIdempotencyFailCommand failCommand(String hash) {
        return new ForgotPasswordIdempotencyFailCommand(
                ForgotPasswordIdempotencyAction.START,
                new ForgotPasswordIdempotencyKey("idem_fp_123"),
                new ForgotPasswordRequestHash(hash),
                NOW.plusSeconds(20),
                Duration.ofMinutes(2));
    }

    private static ForgotPasswordIdempotencyUnknownOutcomeCommand unknownCommand(String hash) {
        return new ForgotPasswordIdempotencyUnknownOutcomeCommand(
                ForgotPasswordIdempotencyAction.START,
                new ForgotPasswordIdempotencyKey("idem_fp_123"),
                new ForgotPasswordRequestHash(hash),
                NOW.plusSeconds(30),
                Duration.ofMinutes(3));
    }

    private static final class InMemoryRedisOperations implements RedisForgotPasswordIdempotencyOperations {
        private final Map<String, String> store = new LinkedHashMap<>();
        private final Map<String, Duration> ttls = new LinkedHashMap<>();
        private String lastKey;

        @Override
        public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
            lastKey = key;
            if (store.containsKey(key)) {
                return Mono.just(false);
            }
            store.put(key, value);
            ttls.put(key, ttl);
            return Mono.just(true);
        }

        @Override
        public Mono<Void> set(String key, String value, Duration ttl) {
            lastKey = key;
            store.put(key, value);
            ttls.put(key, ttl);
            return Mono.empty();
        }

        @Override
        public Mono<Optional<String>> get(String key) {
            return Mono.just(Optional.ofNullable(store.get(key)));
        }

        @Override
        public Mono<Void> delete(String key) {
            store.remove(key);
            ttls.remove(key);
            return Mono.empty();
        }

        private Duration ttlFor(String key) {
            return ttls.get(key);
        }

        private void clear() {
            store.clear();
            ttls.clear();
            lastKey = null;
        }
    }

    private static final class FailingRedisOperations implements RedisForgotPasswordIdempotencyOperations {
        @Override
        public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
            return Mono.error(new RuntimeException("boom " + key));
        }

        @Override
        public Mono<Void> set(String key, String value, Duration ttl) {
            return Mono.error(new RuntimeException("boom " + key));
        }

        @Override
        public Mono<Optional<String>> get(String key) {
            return Mono.error(new RuntimeException("boom " + key));
        }

        @Override
        public Mono<Void> delete(String key) {
            return Mono.error(new RuntimeException("boom " + key));
        }
    }
}