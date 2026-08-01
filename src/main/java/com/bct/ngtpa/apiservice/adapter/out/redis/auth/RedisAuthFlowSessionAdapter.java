package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.InitializeLegacyAuthFlowSessionRevisionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.LegacyAuthFlowSessionRevisionInitializationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.LegacyAuthFlowSessionRevisionInitializationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SecureSessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionDeleteCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionDeleteResult;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision.RevisionedAuthFlowSessionRedisOperations;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

public class RedisAuthFlowSessionAdapter implements AuthFlowSessionPort {

    private final CachePort cachePort;
    private final RedisCacheKeyFactory keyFactory;
    private final RedisAuthFlowSessionJsonMapper jsonMapper;
    private final RevisionedAuthFlowSessionRedisOperations revisionOperations;
    private final SessionRevisionGenerator revisionGenerator;
    private final AuthFlowSessionPersistenceCompatibilityState compatibilityState;
    private final Clock clock;

    public RedisAuthFlowSessionAdapter(
            CachePort cachePort,
            RedisCacheKeyFactory keyFactory,
            RedisAuthFlowSessionJsonMapper jsonMapper) {
        this(cachePort, keyFactory, jsonMapper, null, new SecureSessionRevisionGenerator(),
                establishedCompatibilityState(), Clock.systemUTC());
    }

    RedisAuthFlowSessionAdapter(
            CachePort cachePort,
            RedisCacheKeyFactory keyFactory,
            RedisAuthFlowSessionJsonMapper jsonMapper,
            Clock clock) {
        this(cachePort, keyFactory, jsonMapper, null, new SecureSessionRevisionGenerator(),
                establishedCompatibilityState(), clock);
    }

    public RedisAuthFlowSessionAdapter(
            CachePort cachePort,
            RedisCacheKeyFactory keyFactory,
            RedisAuthFlowSessionJsonMapper jsonMapper,
            RevisionedAuthFlowSessionRedisOperations revisionOperations,
            SessionRevisionGenerator revisionGenerator,
            AuthFlowSessionPersistenceCompatibilityState compatibilityState) {
        this(cachePort, keyFactory, jsonMapper, revisionOperations, revisionGenerator,
                compatibilityState, Clock.systemUTC());
    }

    public RedisAuthFlowSessionAdapter(
            CachePort cachePort,
            RedisCacheKeyFactory keyFactory,
            RedisAuthFlowSessionJsonMapper jsonMapper,
            RevisionedAuthFlowSessionRedisOperations revisionOperations,
            SessionRevisionGenerator revisionGenerator,
            AuthFlowSessionPersistenceCompatibilityState compatibilityState,
            Clock clock) {
        this.cachePort = Objects.requireNonNull(cachePort, "cachePort must not be null");
        this.keyFactory = Objects.requireNonNull(keyFactory, "keyFactory must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper must not be null");
        this.revisionOperations = revisionOperations;
        this.revisionGenerator = Objects.requireNonNull(revisionGenerator, "revisionGenerator must not be null");
        this.compatibilityState = Objects.requireNonNull(compatibilityState, "compatibilityState must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
        return createRevisioned(command).map(AuthFlowSessionSnapshot::session);
    }

    @Override
    public Mono<AuthFlowSessionSnapshot> createRevisioned(AuthFlowSessionCreateCommand command) {
        return Mono.defer(() -> {
            compatibilityState.requireEstablished();
            AuthFlowSession session = new AuthFlowSession(
                    command.authFlowSessionId(), command.scenario(), command.initialStatus(),
                    command.csrfTokenHash(), command.activeOtpChallengeId(), command.data(),
                    command.createdAt(), command.createdAt(), command.expiresAt());
            SessionRevision revision = revisionGenerator.generate();
            Duration ttl = ttlUntil(Instant.now(clock), session.expiresAt());
            String json = jsonMapper.toJson(session, revision);
            if (revisionOperations == null) {
                return cachePort.set(keyFor(session.authFlowSessionId()), json, ttl)
                        .thenReturn(new AuthFlowSessionSnapshot(session, revision));
            }
            return revisionOperations.createIfAbsent(keyFor(session.authFlowSessionId()), json, ttl)
                    .flatMap(created -> created
                            ? Mono.just(new AuthFlowSessionSnapshot(session, revision))
                            : Mono.error(new IllegalStateException("Auth flow session already exists")));
        }).onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId authFlowSessionId) {
        return Mono.defer(() -> cachePort.get(keyFor(authFlowSessionId))
                .map(optionalJson -> optionalJson.map(jsonMapper::fromJson)))
                .onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId authFlowSessionId) {
        return Mono.defer(() -> {
            compatibilityState.requireEstablished();
            return cachePort.get(keyFor(authFlowSessionId))
                    .map(optionalJson -> optionalJson.flatMap(jsonMapper::snapshotFromJson));
        }).onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<LegacyAuthFlowSessionRevisionInitializationResult> initializeLegacyRevision(
            InitializeLegacyAuthFlowSessionRevisionCommand command) {
        return Mono.defer(() -> {
            compatibilityState.requireEstablished();
            if (revisionOperations == null) {
                return Mono.just(LegacyAuthFlowSessionRevisionInitializationResult.outcome(
                        LegacyAuthFlowSessionRevisionInitializationOutcome.SYSTEM_FAILURE));
            }
            String sessionKey = keyFor(command.expectedLegacySession().authFlowSessionId());
            return cachePort.get(sessionKey).flatMap(optionalJson -> {
                if (optionalJson.isEmpty()) {
                    return Mono.just(LegacyAuthFlowSessionRevisionInitializationResult.outcome(
                            LegacyAuthFlowSessionRevisionInitializationOutcome.SESSION_NOT_FOUND));
                }
                String currentJson = optionalJson.orElseThrow();
                if (jsonMapper.snapshotFromJson(currentJson).isPresent()) {
                    return Mono.just(LegacyAuthFlowSessionRevisionInitializationResult.outcome(
                            LegacyAuthFlowSessionRevisionInitializationOutcome.REVISION_ALREADY_PRESENT));
                }
                AuthFlowSession current = jsonMapper.fromJson(currentJson);
                if (!current.equals(command.expectedLegacySession())) {
                    return Mono.just(LegacyAuthFlowSessionRevisionInitializationResult.outcome(
                            LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_INITIALIZATION_CONFLICT));
                }
                SessionRevision revision = revisionGenerator.generate();
                String revisedJson = jsonMapper.toJson(current, revision);
                return revisionOperations.initializeLegacy(sessionKey,
                                command.mutationLockHandle().lockKey(),
                                command.mutationLockHandle().ownershipToken(),
                                currentJson, revisedJson)
                        .map(code -> initializationResult(code, current, revision));
            });
        }).onErrorReturn(LegacyAuthFlowSessionRevisionInitializationResult.outcome(
                LegacyAuthFlowSessionRevisionInitializationOutcome.SYSTEM_FAILURE));
    }

    @Override
    public Mono<AuthFlowSessionMutationResult> updateRevisioned(
            RevisionedAuthFlowSessionMutationCommand command) {
        return revisionedReplace(command);
    }

    @Override
    public Mono<AuthFlowSessionMutationResult> touchRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        AuthFlowSession session = expected.session();
        return revisionedReplace(new RevisionedAuthFlowSessionMutationCommand(expected,
                copy(session, session.status(), now)));
    }

    @Override
    public Mono<AuthFlowSessionMutationResult> completeRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (expected.session().isTerminal()) {
            return Mono.just(AuthFlowSessionMutationResult.outcome(
                    AuthFlowSessionMutationOutcome.INVALID_TRANSITION));
        }
        return revisionedReplace(new RevisionedAuthFlowSessionMutationCommand(expected,
                copy(expected.session(), AuthFlowStatus.COMPLETED, now)));
    }

    @Override
    public Mono<AuthFlowSessionMutationResult> cancelRevisioned(AuthFlowSessionSnapshot expected, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (expected.session().isTerminal()) {
            return Mono.just(AuthFlowSessionMutationResult.outcome(
                    AuthFlowSessionMutationOutcome.INVALID_TRANSITION));
        }
        return revisionedReplace(new RevisionedAuthFlowSessionMutationCommand(expected,
                copy(expected.session(), AuthFlowStatus.CANCELLED, now)));
    }

    @Override
    public Mono<AuthFlowSessionDeleteResult> deleteRevisioned(RevisionedAuthFlowSessionDeleteCommand command) {
        return Mono.defer(() -> {
            compatibilityState.requireEstablished();
            if (revisionOperations == null) {
                return Mono.just(new AuthFlowSessionDeleteResult(AuthFlowSessionMutationOutcome.SYSTEM_FAILURE));
            }
            String expectedJson = jsonMapper.toJson(command.expected().session(), command.expected().revision());
            return revisionOperations.deleteIfCurrent(keyFor(command.expected().session().authFlowSessionId()), expectedJson)
                    .map(code -> new AuthFlowSessionDeleteResult(mapMutationCode(code)));
        }).onErrorReturn(new AuthFlowSessionDeleteResult(AuthFlowSessionMutationOutcome.SYSTEM_FAILURE));
    }

    private Mono<AuthFlowSessionMutationResult> revisionedReplace(
            RevisionedAuthFlowSessionMutationCommand command) {
        return Mono.defer(() -> {
            compatibilityState.requireEstablished();
            if (revisionOperations == null) {
                return Mono.just(AuthFlowSessionMutationResult.outcome(
                        AuthFlowSessionMutationOutcome.SYSTEM_FAILURE));
            }
            String expectedJson = jsonMapper.toJson(command.expected().session(), command.expected().revision());
            SessionRevision next = revisionGenerator.generate();
            String revisedJson = jsonMapper.toJson(command.updated(), next);
            return revisionOperations.replaceIfCurrent(
                            keyFor(command.updated().authFlowSessionId()), expectedJson, revisedJson)
                    .map(code -> code == 1L
                            ? new AuthFlowSessionMutationResult(AuthFlowSessionMutationOutcome.APPLIED,
                                    new AuthFlowSessionSnapshot(command.updated(), next))
                            : AuthFlowSessionMutationResult.outcome(mapMutationCode(code)));
        }).onErrorReturn(AuthFlowSessionMutationResult.outcome(AuthFlowSessionMutationOutcome.SYSTEM_FAILURE));
    }

    private static AuthFlowSessionMutationOutcome mapMutationCode(long code) {
        return switch ((int) code) {
            case 1 -> AuthFlowSessionMutationOutcome.APPLIED;
            case -1 -> AuthFlowSessionMutationOutcome.SESSION_NOT_FOUND;
            case 0 -> AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;
            case -3 -> AuthFlowSessionMutationOutcome.CORRUPT_STATE;
            default -> AuthFlowSessionMutationOutcome.SYSTEM_FAILURE;
        };
    }

    private static AuthFlowSession copy(AuthFlowSession session, AuthFlowStatus status, Instant updatedAt) {
        return new AuthFlowSession(session.authFlowSessionId(), session.scenario(), status,
                session.csrfTokenHash(), session.activeOtpChallengeId(), session.data(),
                session.createdAt(), updatedAt, session.expiresAt());
    }

    @Override
    public Mono<AuthFlowSession> update(AuthFlowSession session) {
        return Mono.defer(() -> persist(session).thenReturn(session))
                .onErrorMap(this::safeFailure);
    }

    @Override
    public Mono<AuthFlowSession> touch(AuthFlowSessionId authFlowSessionId, Instant now) {
        return mutateExisting(authFlowSessionId, now, (session, timestamp) -> new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                session.status(),
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                session.data(),
                session.createdAt(),
                timestamp,
                session.expiresAt()));
    }

    @Override
    public Mono<AuthFlowSession> complete(AuthFlowSessionId authFlowSessionId, Instant now) {
        return mutateExisting(authFlowSessionId, now, (session, timestamp) -> new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                AuthFlowStatus.COMPLETED,
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                session.data(),
                session.createdAt(),
                timestamp,
                session.expiresAt()));
    }

    @Override
    public Mono<AuthFlowSession> cancel(AuthFlowSessionId authFlowSessionId, Instant now) {
        return mutateExisting(authFlowSessionId, now, (session, timestamp) -> new AuthFlowSession(
                session.authFlowSessionId(),
                session.scenario(),
                AuthFlowStatus.CANCELLED,
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                session.data(),
                session.createdAt(),
                timestamp,
                session.expiresAt()));
    }

    @Override
    public Mono<Void> delete(AuthFlowSessionId authFlowSessionId) {
        return Mono.defer(() -> cachePort.evict(keyFor(authFlowSessionId)).then())
                .onErrorMap(this::safeFailure);
    }

    private Mono<AuthFlowSession> mutateExisting(AuthFlowSessionId id, Instant now, SessionMutation mutation) {
        Objects.requireNonNull(now, "now must not be null");
        return load(id)
                .flatMap(optional -> optional
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new IllegalStateException("Auth flow session not found"))))
                .map(session -> mutation.apply(session, now))
                .flatMap(mutated -> persist(mutated, now).thenReturn(mutated));
    }

    private Mono<Void> persist(AuthFlowSession session) {
        return persist(session, Instant.now(clock));
    }

    private Mono<Void> persist(AuthFlowSession session, Instant ttlReferenceTime) {
        if (session == null) {
            return Mono.error(new IllegalArgumentException("Auth flow session must not be null"));
        }
        Duration ttl = ttlUntil(ttlReferenceTime, session.expiresAt());
        return cachePort.set(keyFor(session.authFlowSessionId()), jsonMapper.toJson(session), ttl);
    }

    private static LegacyAuthFlowSessionRevisionInitializationResult initializationResult(
            long code, AuthFlowSession session, SessionRevision revision) {
        if (code == 1L) {
            return new LegacyAuthFlowSessionRevisionInitializationResult(
                    LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_REVISION_INITIALIZED,
                    new AuthFlowSessionSnapshot(session, revision));
        }
        var outcome = switch ((int) code) {
            case -2 -> LegacyAuthFlowSessionRevisionInitializationOutcome.LOCK_OWNERSHIP_LOST;
            case -1 -> LegacyAuthFlowSessionRevisionInitializationOutcome.SESSION_NOT_FOUND;
            case 0 -> LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_INITIALIZATION_CONFLICT;
            case -3 -> LegacyAuthFlowSessionRevisionInitializationOutcome.CORRUPT_STATE;
            default -> LegacyAuthFlowSessionRevisionInitializationOutcome.SYSTEM_FAILURE;
        };
        return LegacyAuthFlowSessionRevisionInitializationResult.outcome(outcome);
    }

    private static AuthFlowSessionPersistenceCompatibilityState establishedCompatibilityState() {
        var state = new AuthFlowSessionPersistenceCompatibilityState();
        state.establish();
        return state;
    }

    private String keyFor(AuthFlowSessionId authFlowSessionId) {
        return RedisAuthFlowSessionKeyFactory.keyFor(keyFactory, authFlowSessionId);
    }

    private Duration ttlUntil(Instant referenceTime, Instant expiresAt) {
        Objects.requireNonNull(referenceTime, "referenceTime must not be null");
        if (expiresAt == null) {
            throw new IllegalArgumentException("Auth flow session expiresAt must not be null");
        }
        Duration ttl = Duration.between(referenceTime, expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Auth flow session TTL must be positive");
        }
        return ttl;
    }

    private Throwable safeFailure(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException) {
            return throwable;
        }
        if (throwable instanceof CacheException cacheException) {
            return cacheException;
        }
        return new CacheException("Auth flow session persistence operation failed", throwable);
    }

    @FunctionalInterface
    private interface SessionMutation {
        AuthFlowSession apply(AuthFlowSession session, Instant now);
    }
}
