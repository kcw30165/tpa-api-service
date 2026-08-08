package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Group 2: create/load/TTL and typed failures.
 *
 * <p>Verifies create persists the whole aggregate with a TTL derived from
 * {@code expiresAt}, never creates a key for a nonpositive remaining TTL, and
 * load distinguishes {@code LOADED}/{@code SESSION_NOT_FOUND}/{@code SYSTEM_FAILURE}
 * without ever swallowing malformed or Redis failures.
 */
class RedisUsernameRecoverySessionAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant CREATED_AT = NOW.minusSeconds(300);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KEY = "ngtpa:username-recovery:session:urs_opaque-1";

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final RedisUsernameRecoverySessionJsonMapper jsonMapper =
            new RedisUsernameRecoverySessionJsonMapper();
    private final RedisUsernameRecoverySessionAdapter adapter = new RedisUsernameRecoverySessionAdapter(
            redis, new RedisCacheKeyFactory("ngtpa"), jsonMapper, CLOCK);

    @Test
    void createPersistsWholeAggregateWithTtlDerivedFromExpiresAt() {
        var session = activeSession(NOW.plusSeconds(1800));
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(1L));

        StepVerifier.create(adapter.create(session))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoverySessionMutationOutcome.CREATED);
                    assertThat(result.session()).isEqualTo(session);
                })
                .verifyComplete();

        verify(redis).execute(
                any(RedisScript.class),
                eq(List.of(KEY)),
                eq(List.of(jsonMapper.toJson(session), "1800000")));
    }

    @Test
    void createRejectsNonpositiveTtlWithoutCreatingKey() {
        var expired = activeSession(NOW.minusSeconds(60));

        StepVerifier.create(adapter.create(expired))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(com.bct.ngtpa.apiservice.exception.CacheException.class)
                        .hasMessage("Username recovery session create failed"))
                .verify();

        verifyNoInteractions(redis);
    }

    @Test
    void loadReturnsNotFoundWhenKeyMissing() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.load(new UsernameRecoverySessionId("urs_opaque-1")))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND))
                .verifyComplete();
    }

    @Test
    void loadReturnsLoadedSession() {
        var session = activeSession(NOW.plusSeconds(1800));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(Mono.just(jsonMapper.toJson(session)));

        StepVerifier.create(adapter.load(new UsernameRecoverySessionId("urs_opaque-1")))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoverySessionMutationOutcome.LOADED);
                    assertThat(result.session()).isEqualTo(session);
                })
                .verifyComplete();
    }

    @Test
    void loadPropagatesMalformedJsonAsSafeCacheFailure() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(Mono.just("{not-json"));

        StepVerifier.create(adapter.load(new UsernameRecoverySessionId("urs_opaque-1")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(com.bct.ngtpa.apiservice.exception.CacheException.class)
                        .hasMessage("Username recovery session state deserialization failed")
                        .hasMessageNotContaining("{not-json"))
                .verify();
    }

    @Test
    void loadPropagatesRedisFailureAsSafeCacheFailure() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(Mono.error(new RuntimeException("redis unavailable")));

        StepVerifier.create(adapter.load(new UsernameRecoverySessionId("urs_opaque-1")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(com.bct.ngtpa.apiservice.exception.CacheException.class)
                        .hasMessage("Username recovery session load failed")
                        .hasMessageNotContaining("redis unavailable"))
                .verify();
    }

    private static UsernameRecoverySession activeSession(Instant expiresAt) {
        var registered = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "HK",
                        "trust-1",
                        true,
                        "user-sch",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null);
        return new UsernameRecoverySession(
                new UsernameRecoverySessionId("urs_opaque-1"),
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash("csrf_hash_opaque"),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"),
                CREATED_AT,
                expiresAt,
                List.of(registered));
    }
}
