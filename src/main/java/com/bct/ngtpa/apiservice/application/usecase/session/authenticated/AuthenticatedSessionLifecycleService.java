package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.CreateAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Owns authenticated-session creation, sliding access, and persisted terminal lifecycle
 * transitions. Terminal records are retained briefly for idempotency and operational inspection and
 * remain unusable.
 */
public final class AuthenticatedSessionLifecycleService
    implements LoadAndTouchAuthenticatedSessionUseCase,
        CreateAuthenticatedSessionUseCase,
        RevokeAuthenticatedSessionUseCase,
        LogoutAuthenticatedSessionUseCase {

  private static final Logger LOG =
      LoggerFactory.getLogger(AuthenticatedSessionLifecycleService.class);

  private final AuthenticatedSessionPort port;
  private final AuthenticatedSessionProtectorPort protector;
  private final AuthenticatedSessionIdGenerator ids;
  private final AuthenticatedSessionRevisionGenerator revisions;
  private final AuthenticatedSessionConcurrencyService concurrency;
  private final Duration idleTimeout;
  private final Duration absoluteTtl;
  private final Duration terminalRetention;
  private final Clock clock;

  public AuthenticatedSessionLifecycleService(
      AuthenticatedSessionPort port,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionIdGenerator ids,
      AuthenticatedSessionRevisionGenerator revisions,
      AuthenticatedSessionConcurrencyService concurrency,
      Duration idleTimeout,
      Duration absoluteTtl,
      Duration terminalRetention,
      Clock clock) {
    this.port = Objects.requireNonNull(port);
    this.protector = Objects.requireNonNull(protector);
    this.ids = Objects.requireNonNull(ids);
    this.revisions = Objects.requireNonNull(revisions);
    this.concurrency = Objects.requireNonNull(concurrency);
    this.idleTimeout = positive(idleTimeout, "idleTimeout");
    this.absoluteTtl = positive(absoluteTtl, "absoluteTtl");
    this.terminalRetention = positive(terminalRetention, "terminalRetention");
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<AuthenticatedSessionLifecycleResult> create(
      CreateAuthenticatedSessionCommand command) {
    return Mono.defer(
        () -> {
          Instant now = clock.instant();
          Instant absolute = now.plus(absoluteTtl);
          Instant idle = min(now.plus(idleTimeout), absolute);
          var session =
              new AuthenticatedSession(
                  command.requestedSessionId() == null
                      ? ids.generate()
                      : command.requestedSessionId(),
                  command.actorContext(),
                  command.accounts(),
                  AuthenticatedSessionStatus.ACTIVE,
                  command.csrfTokenHash(),
                  now,
                  now,
                  idle,
                  absolute,
                  revisions.generate(),
                  null,
                  Map.of());
          return port.store(
                  new StoreAuthenticatedSessionCommand(
                      session.authenticatedSessionId(),
                      protector.protect(session),
                      Duration.between(now, idle),
                      now,
                      command.correlationId()))
              .flatMap(
                  persistence -> {
                    if (persistence == null
                        || persistence.status() != AuthenticatedSessionPersistenceStatus.STORED) {
                      LOG.error(
                          "event=authenticated.session.create.rejected stage=redis-store persistenceStatus={} correlationId={}",
                          persistence == null ? null : persistence.status(),
                          command.correlationId());
                      return result(AuthenticatedSessionLifecycleStatus.FAILED);
                    }
                    return Mono.just(AuthenticatedSessionLifecycleResult.active(session));
                  })
              .doOnError(
                  error ->
                      LOG.error(
                          "event=authenticated.session.create.failure stage=redis-store exceptionType={} message={} correlationId={}",
                          error.getClass().getSimpleName(),
                          error.getMessage(),
                          command.correlationId(),
                          error))
              .onErrorResume(error -> result(AuthenticatedSessionLifecycleStatus.FAILED));
        });
  }

  @Override
  public Mono<AuthenticatedSessionLifecycleResult> loadAndTouch(
      LoadAndTouchAuthenticatedSessionCommand command) {
    return updateLifecycle(command.authenticatedSessionId(), null, command.correlationId())
        .doOnError(
            error ->
                LOG.error(
                    "event=authenticated.session.load-and-touch.failure stage=load-and-touch exceptionType={} causeType={} correlationId={}",
                    error.getClass().getSimpleName(),
                    error.getCause() == null ? null : error.getCause().getClass().getSimpleName(),
                    command.correlationId()))
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
      AuthenticatedSessionId sessionId, AuthenticatedSessionStatus target, String correlationId) {
    return updateLifecycle(sessionId, target, correlationId)
        .onErrorResume(error -> result(AuthenticatedSessionLifecycleStatus.FAILED));
  }

  private Mono<AuthenticatedSessionLifecycleResult> updateLifecycle(
      AuthenticatedSessionId sessionId,
      AuthenticatedSessionStatus target,
      String correlationId) {
    return Mono.defer(() -> {
      // One transition timestamp per subscription; revision retries cannot renew retention.
      var terminalAt = new AtomicReference<Instant>();
      return concurrency.updateIfEligible(
          sessionId,
          (session, now) -> !session.status().terminal(),
          (session, now) -> {
            if (!session.isUsable(now) || target != null) {
              terminalAt.compareAndSet(null, now);
              return copy(session,
                  session.isUsable(now) ? target : AuthenticatedSessionStatus.EXPIRED,
                  session.lastAccessedAt(), session.idleExpiresAt());
            }
            return copy(session, AuthenticatedSessionStatus.ACTIVE, now,
                min(now.plus(idleTimeout), session.absoluteExpiresAt()));
          },
          session -> session.status().terminal()
              ? terminalAt.get().plus(terminalRetention)
              : session.idleExpiresAt(),
          correlationId)
          .map(update -> switch (update.outcome()) {
            case APPLIED -> new AuthenticatedSessionLifecycleResult(
                lifecycleStatus(update.session().status()), update.session());
            case UNCHANGED -> AuthenticatedSessionLifecycleResult.of(
                lifecycleStatus(update.session().status()));
            case SESSION_NOT_FOUND -> AuthenticatedSessionLifecycleResult.of(
                AuthenticatedSessionLifecycleStatus.NOT_FOUND);
            default -> AuthenticatedSessionLifecycleResult.of(AuthenticatedSessionLifecycleStatus.FAILED);
          });
    });
  }

  private AuthenticatedSession copy(
      AuthenticatedSession source,
      AuthenticatedSessionStatus status,
      Instant lastAccessedAt,
      Instant idleExpiresAt) {
    // The shared conditional writer stamps the fresh revision after this mutation.
    return new AuthenticatedSession(
        source.authenticatedSessionId(),
        source.actorContext(),
        source.accounts(),
        status,
        source.csrfTokenHash(),
        source.createdAt(),
        lastAccessedAt,
        idleExpiresAt,
        source.absoluteExpiresAt(),
        source.revision(),
        source.fundPriceModeMetadata(),
        source.featureState());
  }

  private AuthenticatedSessionLifecycleStatus lifecycleStatus(AuthenticatedSessionStatus status) {
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
