package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateCreateCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateDeleteResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateLoadResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationCommand;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationOutcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateMutationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStatePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

/**
 * Redis create/load adapter for the protected Username Recovery workflow-state
 * sidecar.
 */
public class RedisUsernameRecoveryWorkflowStateAdapter
                implements UsernameRecoveryWorkflowStatePort {

        private static final DefaultRedisScript<Long> CREATE = script("redis/auth-flow-session/create-revisioned.lua");
        private static final DefaultRedisScript<Long> UPDATE = script("redis/username-recovery-session/update.lua");

        private final ReactiveStringRedisTemplate redis;
        private final RedisCacheKeyFactory keyFactory;
        private final RedisUsernameRecoveryWorkflowStateJsonMapper jsonMapper;
        private final Clock clock;

        public RedisUsernameRecoveryWorkflowStateAdapter(
                        ReactiveStringRedisTemplate redis,
                        RedisCacheKeyFactory keyFactory,
                        RedisUsernameRecoveryWorkflowStateJsonMapper jsonMapper) {
                this(redis, keyFactory, jsonMapper, Clock.systemUTC());
        }

        RedisUsernameRecoveryWorkflowStateAdapter(
                        ReactiveStringRedisTemplate redis,
                        RedisCacheKeyFactory keyFactory,
                        RedisUsernameRecoveryWorkflowStateJsonMapper jsonMapper,
                        Clock clock) {
                this.redis = Objects.requireNonNull(redis, "redis must not be null");
                this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
                this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
                this.clock = Objects.requireNonNull(clock, "clock must not be null");
        }

        @Override
        public Mono<UsernameRecoveryWorkflowStateMutationResult> create(
                        UsernameRecoveryWorkflowStateCreateCommand command) {
                return Mono.defer(() -> {
                        Objects.requireNonNull(command, "command must not be null");
                        Duration ttl = Duration.between(clock.instant(), command.expiresAt());
                        if (ttl.isZero() || ttl.isNegative()) {
                                throw new CacheException("Username recovery workflow state create failed");
                        }
                        String key = keyFor(command.state().authFlowSessionId());
                        String json = jsonMapper.toJson(command.state());
                        return redis.execute(
                                        CREATE, List.of(key),
                                        List.of(json, Long.toString(ttl.toMillis())))
                                        .next()
                                        .map(code -> mapCreateCode(code, command))
                                        .defaultIfEmpty(systemFailure());
                })
                                .onErrorMap(error -> persistenceFailure("create", error));
        }

        @Override
        public Mono<UsernameRecoveryWorkflowStateLoadResult> load(
                        AuthFlowSessionId authFlowSessionId) {
                return Mono.defer(() -> {
                        Objects.requireNonNull(authFlowSessionId,
                                        "authFlowSessionId must not be null");
                        return redis.opsForValue().get(keyFor(authFlowSessionId))
                                        .map(json -> UsernameRecoveryWorkflowStateLoadResult.loaded(
                                                        jsonMapper.fromJson(json)))
                                        .defaultIfEmpty(UsernameRecoveryWorkflowStateLoadResult.notFound());
                })
                                .onErrorMap(error -> persistenceFailure("load", error));
        }

        @Override
        public Mono<UsernameRecoveryWorkflowStateMutationResult> update(
                        UsernameRecoveryWorkflowStateMutationCommand command) {
                return Mono.defer(() -> {
                        Objects.requireNonNull(command, "command must not be null");
                        String revisedJson = jsonMapper.toJson(command.updatedState());
                        return redis.execute(
                                        UPDATE,
                                        List.of(keyFor(command.authFlowSessionId())),
                                        List.of(
                                                        Long.toString(command.expectedRevision().value()),
                                                        revisedJson))
                                        .next()
                                        .map(code -> mapUpdateCode(code, command))
                                        .defaultIfEmpty(systemFailure());
                })
                                .onErrorMap(error -> persistenceFailure("update", error));
        }

        @Override
        public Mono<UsernameRecoveryWorkflowStateDeleteResult> delete(
                        AuthFlowSessionId authFlowSessionId) {
                return Mono.error(new UnsupportedOperationException(
                                "Workflow-state delete is not implemented in this repair"));
        }

        private String keyFor(AuthFlowSessionId authFlowSessionId) {
                return RedisUsernameRecoveryWorkflowStateKeyFactory.keyFor(
                                keyFactory, authFlowSessionId);
        }

        private static UsernameRecoveryWorkflowStateMutationResult mapCreateCode(
                        Long code, UsernameRecoveryWorkflowStateCreateCommand command) {
                if (code == null) {
                        return systemFailure();
                }
                return switch (code.intValue()) {
                        case 1 -> new UsernameRecoveryWorkflowStateMutationResult(
                                        UsernameRecoveryWorkflowStateMutationOutcome.CREATED, command.state());
                        case 0 -> UsernameRecoveryWorkflowStateMutationResult.outcome(
                                        UsernameRecoveryWorkflowStateMutationOutcome.STATE_ALREADY_EXISTS);
                        default -> systemFailure();
                };
        }

        private static UsernameRecoveryWorkflowStateMutationResult mapUpdateCode(
                        Long code, UsernameRecoveryWorkflowStateMutationCommand command) {
                if (code == null) {
                        return systemFailure();
                }
                return switch (code.intValue()) {
                        case 1 -> new UsernameRecoveryWorkflowStateMutationResult(
                                        UsernameRecoveryWorkflowStateMutationOutcome.APPLIED,
                                        command.updatedState());
                        case 0 -> UsernameRecoveryWorkflowStateMutationResult.outcome(
                                        UsernameRecoveryWorkflowStateMutationOutcome.REVISION_CONFLICT);
                        case -1, -3 -> UsernameRecoveryWorkflowStateMutationResult.outcome(
                                        UsernameRecoveryWorkflowStateMutationOutcome.STATE_NOT_FOUND);
                        default -> systemFailure();
                };
        }

        private static UsernameRecoveryWorkflowStateMutationResult systemFailure() {
                return UsernameRecoveryWorkflowStateMutationResult.outcome(
                                UsernameRecoveryWorkflowStateMutationOutcome.SYSTEM_FAILURE);
        }

        private static CacheException persistenceFailure(String operation, Throwable error) {
                if (error instanceof CacheException cacheException) {
                        return cacheException;
                }
                return new CacheException(
                                "Username recovery workflow state " + operation + " failed", error);
        }

        private static DefaultRedisScript<Long> script(String path) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setLocation(new ClassPathResource(path));
                script.setResultType(Long.class);
                return script;
        }
}
