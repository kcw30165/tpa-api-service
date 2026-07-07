package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RedisCaptchaChallengeAdapterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC);
    private static final String KEY = "ngtpa:auth:captcha:cap_abcdefghijklmnop";
    private static final CaptchaChallengeId CHALLENGE_ID = new CaptchaChallengeId("cap_abcdefghijklmnop");
    private static final CaptchaAnswerHash ANSWER_HASH =
            new CaptchaAnswerHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    private final InMemoryRedisCaptchaChallengeOperations redis = new InMemoryRedisCaptchaChallengeOperations();
    private final RedisCaptchaChallengeJsonMapper jsonMapper = new RedisCaptchaChallengeJsonMapper();
    private final RedisCaptchaChallengeAdapter adapter = new RedisCaptchaChallengeAdapter(
            new RedisCacheKeyFactory("ngtpa"),
            jsonMapper,
            redis,
            FIXED_CLOCK);

    @Test
    void createStoresChallengeWithTtl() {
        var challenge = createdChallenge(Instant.parse("2026-07-07T00:05:00Z"));

        StepVerifier.create(adapter.create(challenge))
                .assertNext(result -> assertThat(result).isEqualTo(challenge))
                .verifyComplete();

        assertThat(redis.ttlFor(KEY)).isEqualTo(Duration.ofMinutes(5));
        assertThat(redis.store.get(KEY))
                .contains("answerHash")
                .doesNotContain("plainAnswer")
                .doesNotContain("imageBase64")
                .doesNotContain("captchaText");
    }

    @Test
    void createRejectsExpiredChallenge() {
        var challenge = new CaptchaChallenge(
            CHALLENGE_ID,
            ANSWER_HASH,
            CaptchaStatus.CREATED,
            0,
            5,
            Instant.parse("2026-07-06T23:59:00Z"),
            Instant.parse("2026-07-07T00:00:00Z"),
            null,
            null,
            null,
            null);

        StepVerifier.create(adapter.create(challenge))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Captcha challenge TTL must be positive"))
                .verify();

        assertThat(redis.store).isEmpty();
    }

    @Test
    void loadReturnsEmptyWhenMissing() {
        StepVerifier.create(adapter.load(CHALLENGE_ID))
                .expectNext(Optional.empty())
                .verifyComplete();
    }

    @Test
    void loadsExistingChallengeRoundTrip() {
        var challenge = new CaptchaChallenge(
                CHALLENGE_ID,
                ANSWER_HASH,
                CaptchaStatus.VERIFIED,
                1,
                5,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:05:00Z"),
                Instant.parse("2026-07-07T00:01:00Z"),
                null,
                null,
                null);
        redis.store.put(KEY, jsonMapper.toJson(challenge));
        redis.ttls.put(KEY, Duration.ofMinutes(4));

        StepVerifier.create(adapter.load(CHALLENGE_ID))
                .assertNext(optional -> assertThat(optional).contains(challenge))
                .verifyComplete();
    }

    @Test
    void markVerifiedUpdatesStatusAndTimestamp() {
        redisCreatedChallenge();

        StepVerifier.create(adapter.markVerified(CHALLENGE_ID, Instant.parse("2026-07-07T00:01:00Z")))
                .assertNext(updated -> {
                    assertThat(updated.status()).isEqualTo(CaptchaStatus.VERIFIED);
                    assertThat(updated.verifiedAt()).isEqualTo(Instant.parse("2026-07-07T00:01:00Z"));
                    assertThat(updated.answerHash()).isEqualTo(ANSWER_HASH);
                })
                .verifyComplete();

        assertThat(redis.ttlFor(KEY)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void incrementFailedAttemptIncrementsCount() {
        redisCreatedChallenge();

        StepVerifier.create(adapter.incrementFailedAttempt(CHALLENGE_ID, Instant.parse("2026-07-07T00:01:00Z")))
                .assertNext(updated -> assertThat(updated.failedAttemptCount()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void markLockedUpdatesStatusAndTimestamp() {
        redisCreatedChallenge();

        StepVerifier.create(adapter.markLocked(CHALLENGE_ID, Instant.parse("2026-07-07T00:02:00Z")))
                .assertNext(updated -> {
                    assertThat(updated.status()).isEqualTo(CaptchaStatus.LOCKED);
                    assertThat(updated.lockedAt()).isEqualTo(Instant.parse("2026-07-07T00:02:00Z"));
                })
                .verifyComplete();
    }

    @Test
    void markConsumedUpdatesStatusAndTimestamp() {
        var verified = new CaptchaChallenge(
                CHALLENGE_ID,
                ANSWER_HASH,
                CaptchaStatus.VERIFIED,
                0,
                5,
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:05:00Z"),
                Instant.parse("2026-07-07T00:01:00Z"),
                null,
                null,
                null);
        redis.store.put(KEY, jsonMapper.toJson(verified));
        redis.ttls.put(KEY, Duration.ofMinutes(4));

        StepVerifier.create(adapter.markConsumed(CHALLENGE_ID, Instant.parse("2026-07-07T00:02:00Z")))
                .assertNext(updated -> {
                    assertThat(updated.status()).isEqualTo(CaptchaStatus.CONSUMED);
                    assertThat(updated.consumedAt()).isEqualTo(Instant.parse("2026-07-07T00:02:00Z"));
                })
                .verifyComplete();
    }

    @Test
    void markRefreshedUpdatesStatusAndTimestamp() {
        redisCreatedChallenge();

        StepVerifier.create(adapter.markRefreshed(CHALLENGE_ID, Instant.parse("2026-07-07T00:03:00Z")))
                .assertNext(updated -> {
                    assertThat(updated.status()).isEqualTo(CaptchaStatus.REFRESHED);
                    assertThat(updated.refreshedAt()).isEqualTo(Instant.parse("2026-07-07T00:03:00Z"));
                })
                .verifyComplete();
    }

    @Test
    void deleteRemovesChallenge() {
        redisCreatedChallenge();

        StepVerifier.create(adapter.delete(CHALLENGE_ID)).verifyComplete();
        StepVerifier.create(adapter.load(CHALLENGE_ID))
                .expectNext(Optional.empty())
                .verifyComplete();
        StepVerifier.create(adapter.delete(CHALLENGE_ID)).verifyComplete();
    }

    @Test
    void malformedRedisJsonFailsSafely() {
        redis.store.put(KEY, "{bad-json");
        redis.ttls.put(KEY, Duration.ofMinutes(4));

        StepVerifier.create(adapter.load(CHALLENGE_ID))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Captcha challenge state deserialization failed")
                        .hasMessageNotContaining("{bad-json"))
                .verify();
    }

    @Test
    void redisFailureDoesNotExposeKeyHashOrJson() {
        var failingAdapter = new RedisCaptchaChallengeAdapter(
                new RedisCacheKeyFactory("ngtpa"),
                jsonMapper,
                new FailingRedisCaptchaChallengeOperations(),
                FIXED_CLOCK);

        StepVerifier.create(failingAdapter.create(createdChallenge(Instant.parse("2026-07-07T00:05:00Z"))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Captcha challenge persistence operation failed")
                        .hasMessageNotContaining(CHALLENGE_ID.value())
                        .hasMessageNotContaining(KEY)
                        .hasMessageNotContaining(ANSWER_HASH.value())
                        .hasMessageNotContaining("imageBase64"))
                .verify();
    }

    private void redisCreatedChallenge() {
        redis.store.put(KEY, jsonMapper.toJson(createdChallenge(Instant.parse("2026-07-07T00:05:00Z"))));
        redis.ttls.put(KEY, Duration.ofMinutes(5));
    }

    private static CaptchaChallenge createdChallenge(Instant expiresAt) {
        return new CaptchaChallenge(
                CHALLENGE_ID,
                ANSWER_HASH,
                CaptchaStatus.CREATED,
                0,
                5,
                Instant.parse("2026-07-07T00:00:00Z"),
                expiresAt,
                null,
                null,
                null,
                null);
    }

    private static final class InMemoryRedisCaptchaChallengeOperations implements RedisCaptchaChallengeOperations {
        private final Map<String, String> store = new LinkedHashMap<>();
        private final Map<String, Duration> ttls = new LinkedHashMap<>();

        @Override
        public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
            if (store.containsKey(key)) {
                return Mono.just(false);
            }
            store.put(key, value);
            ttls.put(key, ttl);
            return Mono.just(true);
        }

        @Override
        public Mono<Void> set(String key, String value, Duration ttl) {
            store.put(key, value);
            ttls.put(key, ttl);
            return Mono.empty();
        }

        @Override
        public Mono<Optional<String>> get(String key) {
            return Mono.just(Optional.ofNullable(store.get(key)));
        }

        @Override
        public Mono<Optional<Duration>> ttl(String key) {
            return Mono.just(Optional.ofNullable(ttls.get(key)));
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
    }

    private static final class FailingRedisCaptchaChallengeOperations implements RedisCaptchaChallengeOperations {
        @Override
        public Mono<Boolean> setIfAbsent(String key, String value, Duration ttl) {
            return Mono.error(new RuntimeException("boom " + key + " " + value));
        }

        @Override
        public Mono<Void> set(String key, String value, Duration ttl) {
            return Mono.error(new RuntimeException("boom " + key + " " + value));
        }

        @Override
        public Mono<Optional<String>> get(String key) {
            return Mono.error(new RuntimeException("boom " + key));
        }

        @Override
        public Mono<Optional<Duration>> ttl(String key) {
            return Mono.error(new RuntimeException("boom " + key));
        }

        @Override
        public Mono<Void> delete(String key) {
            return Mono.error(new RuntimeException("boom " + key));
        }
    }
}