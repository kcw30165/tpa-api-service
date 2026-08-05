package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.junit.jupiter.api.Test;

class RedisAuthFlowSessionAdapterTest {

        private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");
        private final InMemoryCachePort cachePort = new InMemoryCachePort();
        private final RedisAuthFlowSessionAdapter adapter = new RedisAuthFlowSessionAdapter(
                        cachePort,
                        new RedisCacheKeyFactory("ngtpa"),
                        new RedisAuthFlowSessionJsonMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC));

        @Test
        void createsAuthFlowSessionWithTtlDerivedFromExpiresAt() {
                var command = createCommand(NOW.plusSeconds(1800));

                StepVerifier.create(adapter.create(command))
                                .assertNext(session -> {
                                        assertThat(session.scenario()).isEqualTo(AuthFlowScenario.REGISTRATION);
                                        assertThat(session.status()).isEqualTo(AuthFlowStatus.OTP_SENT);
                                        assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
                                })
                                .verifyComplete();

                assertThat(cachePort.ttlFor("ngtpa:auth:flow-session:afs_abc123")).isEqualTo(Duration.ofSeconds(1800));
        }

        @Test
        void createRejectsExpiredSession() {
                var command = createCommand(NOW);

                StepVerifier.create(adapter.create(command))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(IllegalArgumentException.class)
                                                .hasMessage("Auth flow session TTL must be positive"))
                                .verify();
                assertThat(cachePort.values).isEmpty();
        }

        @Test
        void loadReturnsEmptyWhenSessionMissing() {
                StepVerifier.create(adapter.load(new AuthFlowSessionId("afs_missing")))
                                .expectNext(Optional.empty())
                                .verifyComplete();
        }

        @Test
        void unversionedUpdateFailsClosed() {
                var updated = session(AuthFlowStatus.OTP_VERIFIED, NOW, NOW.plusSeconds(1800), "otp_new");

                StepVerifier.create(adapter.update(updated))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(UnsupportedOperationException.class)
                                                .hasMessage("Unversioned auth-flow update is not supported"))
                                .verify();
        }

        @Test
        void unversionedTouchFailsClosed() {
                StepVerifier.create(adapter.touch(new AuthFlowSessionId("afs_abc123"), NOW.plusSeconds(300)))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(UnsupportedOperationException.class)
                                                .hasMessage("Unversioned auth-flow touch is not supported"))
                                .verify();
        }

        @Test
        void unversionedCompleteFailsClosed() {
                StepVerifier.create(adapter.complete(new AuthFlowSessionId("afs_abc123"), NOW.plusSeconds(120)))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(UnsupportedOperationException.class)
                                                .hasMessage("Unversioned auth-flow complete is not supported"))
                                .verify();
        }

        @Test
        void unversionedCancelFailsClosed() {
                StepVerifier.create(adapter.cancel(new AuthFlowSessionId("afs_abc123"), NOW.plusSeconds(180)))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(UnsupportedOperationException.class)
                                                .hasMessage("Unversioned auth-flow cancel is not supported"))
                                .verify();
        }

        @Test
        void unversionedDeleteFailsClosed() {
                StepVerifier.create(adapter.delete(new AuthFlowSessionId("afs_abc123")))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(UnsupportedOperationException.class)
                                                .hasMessage("Unversioned auth-flow delete is not supported"))
                                .verify();
        }

        @Test
        void unversionedUpdateFailsBeforeRedisAccess() {
                cachePort.failSet = true;

                StepVerifier.create(adapter.update(
                                session(AuthFlowStatus.OTP_SENT, NOW, NOW.plusSeconds(1800), "otp_secret")))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(UnsupportedOperationException.class)
                                                .hasMessage("Unversioned auth-flow update is not supported")
                                                .hasMessageNotContaining("afs_abc123")
                                                .hasMessageNotContaining("otp_secret"))
                                .verify();
                assertThat(cachePort.values).isEmpty();
        }

        private static AuthFlowSessionCreateCommand createCommand(Instant expiresAt) {
                return new AuthFlowSessionCreateCommand(
                                new AuthFlowSessionId("afs_abc123"),
                                AuthFlowScenario.REGISTRATION,
                                AuthFlowStatus.OTP_SENT,
                                NOW,
                                expiresAt,
                                data(),
                                "csrf_hash_value",
                                "otp_abc");
        }

        private static AuthFlowSession session(AuthFlowStatus status, Instant updatedAt, Instant expiresAt,
                        String otpId) {
                return new AuthFlowSession(
                                new AuthFlowSessionId("afs_abc123"),
                                AuthFlowScenario.REGISTRATION,
                                status,
                                "csrf_hash_value",
                                otpId,
                                data(),
                                NOW,
                                updatedAt,
                                expiresAt);
        }

        private static AuthFlowSessionData data() {
                var checkedAt = NOW.plusSeconds(300);
                return new AuthFlowSessionData(
                                "EMAIL",
                                "m****r@example.com",
                                Map.of(
                                                "safe", "value",
                                                "usernameCheckCiphertext", "encrypted-username-check"));
        }

        private static final class InMemoryCachePort implements CachePort {
                private final Map<String, String> values = new LinkedHashMap<>();
                private final Map<String, Duration> ttls = new LinkedHashMap<>();
                private boolean failSet;

                Duration ttlFor(String key) {
                        return ttls.get(key);
                }

                @Override
                public Mono<Optional<String>> get(String cacheKey) {
                        return Mono.just(Optional.ofNullable(values.get(cacheKey)));
                }

                @Override
                public Mono<Void> set(String cacheKey, String value, Duration ttl) {
                        if (failSet) {
                                return Mono.error(new RuntimeException(
                                                "boom afs_abc123 ngtpa:auth:flow-session:afs_abc123 csrf_hash_value otp_secret user_secret"));
                        }
                        values.put(cacheKey, value);
                        ttls.put(cacheKey, ttl);
                        return Mono.empty();
                }

                @Override
                public Mono<Boolean> evict(String cacheKey) {
                        boolean existed = values.remove(cacheKey) != null;
                        ttls.remove(cacheKey);
                        return Mono.just(existed);
                }
        }
}
