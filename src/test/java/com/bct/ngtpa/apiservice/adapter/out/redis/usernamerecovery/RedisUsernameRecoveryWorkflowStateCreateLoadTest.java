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
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateCreateCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationOutcome;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
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

class RedisUsernameRecoveryWorkflowStateCreateLoadTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final String KEY =
            "ngtpa:username-recovery:workflow-state:afs_abc123";
    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final RedisUsernameRecoveryWorkflowStateJsonMapper mapper =
            new RedisUsernameRecoveryWorkflowStateJsonMapper();
    private final RedisUsernameRecoveryWorkflowStateAdapter adapter =
            new RedisUsernameRecoveryWorkflowStateAdapter(
                    redis, new RedisCacheKeyFactory("ngtpa"), mapper,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createPersistsSidecarWithRemainingEnvelopeTtl() {
        var state = state();
        var command = new UsernameRecoveryWorkflowStateCreateCommand(
                state, NOW.plusSeconds(600));
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));

        StepVerifier.create(adapter.create(command))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(
                            UsernameRecoveryWorkflowStateMutationOutcome.CREATED);
                    assertThat(result.state()).isEqualTo(state);
                })
                .verifyComplete();

        verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)),
                eq(List.of(mapper.toJson(state), "600000")));
    }

    @Test
    void duplicateCreateReturnsDeterministicAlreadyExists() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(0L));
        StepVerifier.create(adapter.create(new UsernameRecoveryWorkflowStateCreateCommand(
                        state(), NOW.plusSeconds(600))))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(
                            UsernameRecoveryWorkflowStateMutationOutcome.STATE_ALREADY_EXISTS);
                    assertThat(result.state()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void createRejectsNonpositiveTtlBeforeRedisAccess() {
        StepVerifier.create(adapter.create(new UsernameRecoveryWorkflowStateCreateCommand(
                        state(), NOW)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery workflow state create failed"))
                .verify();
        verifyNoInteractions(redis);
    }

    @Test
    void loadReturnsLoadedStateAndNotFound() {
        var state = state();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(KEY)).thenReturn(Mono.just(mapper.toJson(state)), Mono.empty());

        StepVerifier.create(adapter.load(new AuthFlowSessionId("afs_abc123")))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(
                            UsernameRecoveryWorkflowStateMutationOutcome.LOADED);
                    assertThat(result.state()).isEqualTo(state);
                }).verifyComplete();
        StepVerifier.create(adapter.load(new AuthFlowSessionId("afs_abc123")))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(
                        UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND))
                .verifyComplete();
    }

    private static UsernameRecoveryWorkflowState state() {
        var candidate = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "HK", "trust-1", true, "user-sch",
                        UsernameRecoveryDeliveryState.AVAILABLE), null, null);
        return new UsernameRecoveryWorkflowState(
                new AuthFlowSessionId("afs_abc123"),
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination(
                        "member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"), List.of(candidate));
    }
}
