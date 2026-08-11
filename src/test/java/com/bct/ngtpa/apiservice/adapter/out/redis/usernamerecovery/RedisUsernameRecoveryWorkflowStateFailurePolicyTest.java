package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateCreateCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationCommand;
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

class RedisUsernameRecoveryWorkflowStateFailurePolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
    private final RedisUsernameRecoveryWorkflowStateAdapter adapter =
            new RedisUsernameRecoveryWorkflowStateAdapter(
                    redis, new RedisCacheKeyFactory("ngtpa"),
                    new RedisUsernameRecoveryWorkflowStateJsonMapper(),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void emptyCreateResultRemainsTypedSystemFailure() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.empty());
        StepVerifier.create(adapter.create(new UsernameRecoveryWorkflowStateCreateCommand(
                        state(), NOW.plusSeconds(600))))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(
                        UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE))
                .verifyComplete();
    }

    @Test
    void redisCreateAndLoadFailuresUseSafeMessages() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("sensitive redis detail")));
        StepVerifier.create(adapter.create(new UsernameRecoveryWorkflowStateCreateCommand(
                        state(), NOW.plusSeconds(600))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery workflow state create failed")
                        .hasMessageNotContaining("sensitive redis detail"))
                .verify();

        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(Mono.error(
                new RuntimeException("another sensitive redis detail")));
        StepVerifier.create(adapter.load(new AuthFlowSessionId("afs_abc123")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery workflow state load failed")
                        .hasMessageNotContaining("another sensitive redis detail"))
                .verify();
    }

    @Test
    void malformedJsonRetainsMapperSafeFailure() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn(Mono.just("{not-json"));
        StepVerifier.create(adapter.load(new AuthFlowSessionId("afs_abc123")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(CacheException.class)
                        .hasMessage("Username recovery workflow state deserialization failed")
                        .hasMessageNotContaining("{not-json"))
                .verify();
    }

    @Test
    void updateAndDeleteFailClosedBeforeRedisAccess() {
        var current = state();
        var updated = current.reserve(new UsernameRecoverySchemeRef("sch_opaque-1"));
        var command = new UsernameRecoveryWorkflowStateMutationCommand(
                current.authFlowSessionId(), current.revision(), updated);
        StepVerifier.create(adapter.update(command))
                .expectError(UnsupportedOperationException.class).verify();
        StepVerifier.create(adapter.delete(current.authFlowSessionId()))
                .expectError(UnsupportedOperationException.class).verify();
        verifyNoInteractions(redis);
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
