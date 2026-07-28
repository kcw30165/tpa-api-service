package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.CreateAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;

import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

/**
 * Owns authenticated-session creation, sliding access, and persisted terminal
 * lifecycle transitions. Terminal records are retained briefly for
 * idempotency and operational inspection and remain unusable.
 */
public final class AuthenticatedSessionLifecycleService
        implements LoadAndTouchAuthenticatedSessionUseCase,
        CreateAuthenticatedSessionUseCase,
        RevokeAuthenticatedSessionUseCase,
        LogoutAuthenticatedSessionUseCase {

    private final AuthenticatedSessionPort port;
    private final AuthenticatedSessionProtectorPort protector;
    private final AuthenticatedSessionIdGenerator ids;
    private final Duration idleTimeout;
    private final Duration absoluteTtl;
    private final Duration terminalRetention;
    private final Clock clock;

    public AuthenticatedSessionLifecycleService(
            AuthenticatedSessionPort port,
            AuthenticatedSessionProtectorPort protector,
            AuthenticatedSessionIdGenerator ids,
            Duration idleTimeout,
            Duration absoluteTtl,
            Duration terminalRetention,
            Clock clock) {
        this.port = Objects.requireNonNull(port);
        this.protector = Objects.requireNonNull(protector);
        this.ids = Objects.requireNonNull(ids);
        this.idleTimeout = positive(idleTimeout, "idleTimeout");
        this.absoluteTtl = positive(absoluteTtl, "absoluteTtl");
        this.terminalRetention = positive(terminalRetention, "terminalRetention");
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Mono<AuthenticatedSessionLifecycleResult> create(
            CreateAuthenticatedSessionCommand command) {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            Instant absolute = now.plus(absoluteTtl);
            Instant idle = min(now.plus(idleTimeout), absolute);
            var session = new AuthenticatedSession(
                    ids.generate(),
                    command.actorContext(),
                    command.accounts(),
                    AuthenticatedSessionStatus.ACTIVE,
                    command.csrfTokenHash(),
                    now,
                    now,
                    idle,
                    absolute);
            return port.store(new StoreAuthenticatedSessionCommand(
                    session.authenticatedSessionId(),
                    protector.protect(session),
                    Duration.between(now, idle),
                    now,
                    command.correlationId()))
                    .thenReturn(AuthenticatedSessionLifecycleResult.active(session));
        });
    }

    @Override
    public Mono<AuthenticatedSessionLifecycleResult> loadAndTouch(
            LoadAndTouchAuthenticatedSessionCommand command) {
        return load(command.authenticatedSessionId(), command.correlationId())
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return result(AuthenticatedSessionLifecycleStatus.NOT_FOUND);
                    }
                    AuthenticatedSession session = optional.get();
                    if (session.status().terminal()) {
                        return result(lifecycleStatus(session.status()));
                    }
                    Instant now = clock.instant();
                    if (!session.isUsable(now)) {
                        return persistTerminal(
                                session,
                                AuthenticatedSessionStatus.EXPIRED,
                                now,
                                command.correlationId());
                    }
                    Instant idle = min(now.plus(idleTimeout), session.absoluteExpiresAt());
                    AuthenticatedSession touched = copy(
                            session,
                            AuthenticatedSessionStatus.ACTIVE,
                            now,
                            idle);
                    return port.touch(new TouchAuthenticatedSessionCommand(
                            touched.authenticatedSessionId(),
                            protector.protect(touched),
                            Duration.between(now, idle),
                            now,
                            command.correlationId()))
                            .thenReturn(AuthenticatedSessionLifecycleResult.active(touched));
                })
                .onErrorResume(error -> result(AuthenticatedSessionLifecycleStatus.FAILED));
    }

    @Override
    public Mono<AuthenticatedSessionLifecycleResult> logout(
            LogoutActiveAuthenticatedSessionCommand command) {
        return transitionTerminal(
                command.authenticatedSessionId(),
                AuthenticatedSessionStatus.LOGGED_OUT,
                command.correlationId());
    }

    @Override
    public Mono<AuthenticatedSessionLifecycleResult> revoke(
            RevokeActiveAuthenticatedSessionCommand command) {
        return transitionTerminal(
                command.authenticatedSessionId(),
                AuthenticatedSessionStatus.REVOKED,
                command.correlationId());
    }

    private Mono<AuthenticatedSessionLifecycleResult> transitionTerminal(
            AuthenticatedSessionId sessionId,
            AuthenticatedSessionStatus target,
            String correlationId) {
        return load(sessionId, correlationId)
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return result(AuthenticatedSessionLifecycleStatus.NOT_FOUND);
                    }
                    AuthenticatedSession session = optional.get();
                    if (session.status().terminal()) {
                        return result(lifecycleStatus(session.status()));
                    }
                    Instant now = clock.instant();
                    if (!session.isUsable(now)) {
                        return persistTerminal(
                                session,
                                AuthenticatedSessionStatus.EXPIRED,
                                now,
                                correlationId);
                    }
                    return persistTerminal(session, target, now, correlationId);
                })
                .onErrorResume(error -> result(AuthenticatedSessionLifecycleStatus.FAILED));
    }

    private Mono<Optional<AuthenticatedSession>> load(
            AuthenticatedSessionId sessionId,
            String correlationId) {
        return port.load(new LoadAuthenticatedSessionCommand(sessionId, correlationId))
                .map(optional -> optional.map(protector::unprotect));
    }

    private Mono<AuthenticatedSessionLifecycleResult> persistTerminal(
            AuthenticatedSession session,
            AuthenticatedSessionStatus target,
            Instant now,
            String correlationId) {
        AuthenticatedSession terminal = copy(
                session,
                target,
                session.lastAccessedAt(),
                session.idleExpiresAt());
        return port.touch(new TouchAuthenticatedSessionCommand(
                terminal.authenticatedSessionId(),
                protector.protect(terminal),
                terminalRetention,
                now,
                correlationId))
                .thenReturn(new AuthenticatedSessionLifecycleResult(
                        lifecycleStatus(target),
                        terminal));
    }

    private AuthenticatedSession copy(
            AuthenticatedSession source,
            AuthenticatedSessionStatus status,
            Instant lastAccessedAt,
            Instant idleExpiresAt) {
        return new AuthenticatedSession(
                source.authenticatedSessionId(),
                source.actorContext(),
                source.accounts(),
                status,
                source.csrfTokenHash(),
                source.createdAt(),
                lastAccessedAt,
                idleExpiresAt,
                source.absoluteExpiresAt());
    }

    private AuthenticatedSessionLifecycleStatus lifecycleStatus(
            AuthenticatedSessionStatus status) {
        return switch (status) {
            case ACTIVE -> AuthenticatedSessionLifecycleStatus.ACTIVE;
            case LOGGED_OUT -> AuthenticatedSessionLifecycleStatus.LOGGED_OUT;
            case REVOKED -> AuthenticatedSessionLifecycleStatus.REVOKED;
            case EXPIRED -> AuthenticatedSessionLifecycleStatus.EXPIRED;
        };
    }

    private Mono<AuthenticatedSessionLifecycleResult> result(
            AuthenticatedSessionLifecycleStatus status) {
        return Mono.just(AuthenticatedSessionLifecycleResult.of(status));
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
