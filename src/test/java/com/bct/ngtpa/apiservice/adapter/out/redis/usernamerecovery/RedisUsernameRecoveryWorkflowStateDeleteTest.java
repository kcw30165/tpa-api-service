package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationOutcome;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class RedisUsernameRecoveryWorkflowStateDeleteTest {

        private static final AuthFlowSessionId SESSION_ID =
                        new AuthFlowSessionId("afs_abc123");
        private static final String KEY =
                        "ngtpa:username-recovery:workflow-state:afs_abc123";
        private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        private final RedisUsernameRecoveryWorkflowStateAdapter adapter =
                        new RedisUsernameRecoveryWorkflowStateAdapter(
                                        redis,
                                        new RedisCacheKeyFactory("ngtpa"),
                                        new RedisUsernameRecoveryWorkflowStateJsonMapper(),
                                        Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));

        @Test
        void deleteReturnsDeletedWhenStateExists() {
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.just(1L));

                StepVerifier.create(adapter.delete(SESSION_ID))
                                .assertNext(result -> assertThat(result.outcome()).isEqualTo(
                                                UsernameRecoveryWorkflowStateMutationOutcome.DELETED))
                                .verifyComplete();

                verify(redis).execute(
                                any(RedisScript.class), eq(List.of(KEY)), eq(List.of()));
        }

        @Test
        void deleteIsIdempotentWhenStateIsAlreadyAbsent() {
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.just(-1L));

                StepVerifier.create(adapter.delete(SESSION_ID))
                                .assertNext(result -> assertThat(result.outcome()).isEqualTo(
                                                UsernameRecoveryWorkflowStateMutationOutcome.ALREADY_ABSENT))
                                .verifyComplete();
        }

        @Test
        void unexpectedDeleteCodeMapsToSystemFailure() {
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.just(42L));

                StepVerifier.create(adapter.delete(SESSION_ID))
                                .assertNext(result -> assertThat(result.outcome()).isEqualTo(
                                                UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE))
                                .verifyComplete();
        }

        @Test
        void redisDeleteFailureUsesSafeMessage() {
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.error(new RuntimeException(
                                                "secret-key afs_abc123 sch_opaque-1 user-sch")));

                StepVerifier.create(adapter.delete(SESSION_ID))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(CacheException.class)
                                                .hasMessage("Username recovery workflow state delete failed")
                                                .hasMessageNotContaining("secret-key")
                                                .hasMessageNotContaining("afs_abc123")
                                                .hasMessageNotContaining("sch_opaque-1")
                                                .hasMessageNotContaining("user-sch"))
                                .verify();
        }

        @Test
        void deleteResultAcceptsOnlyDeleteOutcomes() {
                assertThat(new UsernameRecoveryWorkflowStateDeleteResult(
                                UsernameRecoveryWorkflowStateMutationOutcome.DELETED).toString())
                                .contains("DELETED");
                assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateDeleteResult(
                                UsernameRecoveryWorkflowStateMutationOutcome.APPLIED))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("outcome is not valid for delete");
                assertThatThrownBy(() -> new UsernameRecoveryWorkflowStateDeleteResult(null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessage("outcome must not be null");
        }
}
