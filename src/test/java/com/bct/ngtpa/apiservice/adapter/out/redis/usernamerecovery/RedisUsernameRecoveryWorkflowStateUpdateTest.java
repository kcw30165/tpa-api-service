package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
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
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class RedisUsernameRecoveryWorkflowStateUpdateTest {

        private static final String KEY = "ngtpa:username-recovery:workflow-state:afs_abc123";
        private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        private final RedisUsernameRecoveryWorkflowStateJsonMapper mapper = new RedisUsernameRecoveryWorkflowStateJsonMapper();
        private final RedisUsernameRecoveryWorkflowStateAdapter adapter = new RedisUsernameRecoveryWorkflowStateAdapter(
                        redis,
                        new RedisCacheKeyFactory("ngtpa"),
                        mapper,
                        Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));

        @Test
        void updateAppliesWithExpectedRevisionAndRevisedJson() {
                var command = command();
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.just(1L));

                StepVerifier.create(adapter.update(command))
                                .assertNext(result -> {
                                        assertThat(result.outcome()).isEqualTo(
                                                        UsernameRecoveryWorkflowStateMutationOutcome.APPLIED);
                                        assertThat(result.state()).isEqualTo(command.updatedState());
                                })
                                .verifyComplete();

                verify(redis).execute(
                                any(RedisScript.class),
                                eq(List.of(KEY)),
                                anyList());
        }

        @Test
        void updateMapsConflictMissingExpiredAndUnexpectedCodes() {
                assertOutcome(0L, UsernameRecoveryWorkflowStateMutationOutcome.REVISION_CONFLICT);
                assertOutcome(-1L, UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND);
                assertOutcome(-3L, UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND);
                assertOutcome(42L, UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE);
        }

        @Test
        void emptyUpdateResultMapsToSystemFailure() {
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.empty());
                StepVerifier.create(adapter.update(command()))
                                .assertNext(result -> assertThat(result.outcome()).isEqualTo(
                                                UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE))
                                .verifyComplete();
        }

        @Test
        void redisUpdateFailureUsesSafeMessage() {
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.error(new RuntimeException(
                                                "secret-key afs_abc123 sch_opaque-1 user-sch")));
                StepVerifier.create(adapter.update(command()))
                                .expectErrorSatisfies(error -> assertThat(error)
                                                .isInstanceOf(CacheException.class)
                                                .hasMessage("Username recovery workflow state update failed")
                                                .hasMessageNotContaining("secret-key")
                                                .hasMessageNotContaining("afs_abc123")
                                                .hasMessageNotContaining("sch_opaque-1")
                                                .hasMessageNotContaining("user-sch"))
                                .verify();
        }

        private void assertOutcome(
                        long code, UsernameRecoveryWorkflowStateMutationOutcome expected) {
                when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                                .thenReturn(Flux.just(code));
                StepVerifier.create(adapter.update(command()))
                                .assertNext(result -> {
                                        assertThat(result.outcome()).isEqualTo(expected);
                                        assertThat(result.state()).isNull();
                                })
                                .verifyComplete();
        }

        private static UsernameRecoveryWorkflowStateMutationCommand command() {
                var current = state();
                var updated = current.reserve(new UsernameRecoverySchemeRef("sch_opaque-1"));
                return new UsernameRecoveryWorkflowStateMutationCommand(
                                current.authFlowSessionId(), current.revision(), updated);
        }

        private static UsernameRecoveryWorkflowState state() {
                var candidate = new UsernameRecoverySessionCandidate(
                                new UsernameRecoveryCandidate(
                                                new UsernameRecoverySchemeRef("sch_opaque-1"),
                                                "HK",
                                                "trust-1",
                                                true,
                                                "user-sch",
                                                UsernameRecoveryDeliveryState.AVAILABLE),
                                null,
                                null);
                return new UsernameRecoveryWorkflowState(
                                new AuthFlowSessionId("afs_abc123"),
                                UsernameRecoveryWorkflowStatus.ACTIVE,
                                new UsernameRecoveryWorkflowStateRevision(0),
                                new UsernameRecoveryVerifiedDestination(
                                                "member@example.com",
                                                UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                                Locale.forLanguageTag("zh-HK"),
                                List.of(candidate));
        }
}
