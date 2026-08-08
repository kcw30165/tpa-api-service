package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionMutationOutcome;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Group 3: atomic compare-and-set update and conflict behavior.
 *
 * <p>Verifies update atomically compares the stored revision with the expected
 * revision, persists the updated session, preserves the remaining expiry (the
 * Lua script rewrites with {@code PTTL -> PSETEX} and never extends the TTL),
 * and maps every script code deterministically.
 */
class RedisUsernameRecoverySessionUpdateTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String KEY = "ngtpa:username-recovery:session:urs_opaque-1";
    private static final UsernameRecoverySessionId SESSION_ID =
            new UsernameRecoverySessionId("urs_opaque-1");

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RedisUsernameRecoverySessionJsonMapper jsonMapper =
            new RedisUsernameRecoverySessionJsonMapper();
    private final RedisUsernameRecoverySessionAdapter adapter = new RedisUsernameRecoverySessionAdapter(
            redis, new RedisCacheKeyFactory("ngtpa"), jsonMapper, CLOCK);

    @Test
    void updateAppliesWhenStoredRevisionMatchesAndPersistsUpdatedSession() {
        var updated = withRevision(activeSession(), new UsernameRecoverySessionRevision(1));
        var command = new UsernameRecoverySessionMutationCommand(
                SESSION_ID, new UsernameRecoverySessionRevision(0), updated);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(1L));

        StepVerifier.create(adapter.update(command))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(UsernameRecoverySessionMutationOutcome.APPLIED);
                    assertThat(result.session()).isEqualTo(updated);
                })
                .verifyComplete();

        verify(redis).execute(
                any(RedisScript.class),
                eq(List.of(KEY)),
                eq(List.of("0", jsonMapper.toJson(updated))));
    }

    @Test
    void updateReturnsRevisionConflictWhenStoredRevisionDiffers() {
        var updated = withRevision(activeSession(), new UsernameRecoverySessionRevision(1));
        var command = new UsernameRecoverySessionMutationCommand(
                SESSION_ID, new UsernameRecoverySessionRevision(0), updated);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(0L));

        StepVerifier.create(adapter.update(command))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.REVISION_CONFLICT))
                .verifyComplete();
    }

    @Test
    void updateReturnsNotFoundWhenKeyMissing() {
        var command = updateCommand();
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(-1L));

        StepVerifier.create(adapter.update(command))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND))
                .verifyComplete();
    }

    @Test
    void updateReturnsNotFoundWhenKeyExpired() {
        var command = updateCommand();
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(-3L));

        StepVerifier.create(adapter.update(command))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND))
                .verifyComplete();
    }

    @Test
    void updateMapsUnexpectedScriptCodeToSystemFailure() {
        var command = updateCommand();
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(42L));

        StepVerifier.create(adapter.update(command))
                .assertNext(result -> assertThat(result.outcome())
                        .isEqualTo(UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE))
                .verifyComplete();
    }

    @Test
    void updatePropagatesRedisFailureAsSafeCacheFailure() {
        var command = updateCommand();
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("redis unavailable")));

        StepVerifier.create(adapter.update(command))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(com.bct.ngtpa.apiservice.exception.CacheException.class)
                        .hasMessage("Username recovery session update failed")
                        .hasMessageNotContaining("redis unavailable"))
                .verify();
    }

    private static UsernameRecoverySessionMutationCommand updateCommand() {
        return new UsernameRecoverySessionMutationCommand(
                SESSION_ID,
                new UsernameRecoverySessionRevision(0),
                withRevision(activeSession(), new UsernameRecoverySessionRevision(1)));
    }

    private static UsernameRecoverySession activeSession() {
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
                SESSION_ID,
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash("csrf_hash_opaque"),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"),
                NOW.minusSeconds(300),
                NOW.plusSeconds(1800),
                List.of(registered));
    }

    private static UsernameRecoverySession withRevision(
            UsernameRecoverySession session, UsernameRecoverySessionRevision revision) {
        return new UsernameRecoverySession(
                session.sessionId(),
                session.scenario(),
                session.status(),
                revision,
                session.csrfHash(),
                session.destination(),
                session.language(),
                session.createdAt(),
                session.expiresAt(),
                session.candidates());
    }
}
