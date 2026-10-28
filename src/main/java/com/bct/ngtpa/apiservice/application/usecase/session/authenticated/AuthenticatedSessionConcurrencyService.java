package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.LoadAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCasUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Generic, reusable reload-per-attempt retry wrapper around {@link
 * AuthenticatedSessionCasUpdatePort}.
 *
 * <p>Mechanism mirrors {@code AuthFlowSessionConcurrencyService} / {@code
 * RevisionAwareAuthFlowSessionWriter}'s proven pattern: the entire load, usability check, mutation,
 * protection, and CAS-write sequence is wrapped in {@code Mono.defer(...)}, so every retry attempt
 * observes freshly reloaded state (and therefore a fresh expected revision) rather than blindly
 * retrying a stale command built from earlier state.
 *
 * <p>Deliberate departures from that precedent: only a genuine revision conflict is retried (other
 * non-{@code APPLIED} CAS outcomes are immediately terminal, since retrying cannot repair them),
 * and this service returns a typed {@link AuthenticatedSessionConcurrencyResult} rather than
 * throwing, for every case the caller is expected to handle.
 */
public final class AuthenticatedSessionConcurrencyService {

  private final AuthenticatedSessionPort sessions;
  private final AuthenticatedSessionProtectorPort protector;
  private final AuthenticatedSessionCasUpdatePort casPort;
  private final AuthenticatedSessionRevisionGenerator revisions;
  private final Clock clock;

  public AuthenticatedSessionConcurrencyService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionCasUpdatePort casPort,
      AuthenticatedSessionRevisionGenerator revisions,
      Clock clock) {
    this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
    this.protector = Objects.requireNonNull(protector, "protector must not be null");
    this.casPort = Objects.requireNonNull(casPort, "casPort must not be null");
    this.revisions = Objects.requireNonNull(revisions, "revisions must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Loads the current session, applies {@code mutator} to it if usable, and writes the replacement
   * via a compare-and-swap. On a genuine revision conflict, the entire sequence is retried (up to 2
   * additional attempts, matching the proven AuthFlowSession backoff) against freshly reloaded
   * state.
   */
  public Mono<AuthenticatedSessionConcurrencyResult> updateIfUsable(
      AuthenticatedSessionId authenticatedSessionId,
      UnaryOperator<AuthenticatedSession> mutator,
      String correlationId) {
    Objects.requireNonNull(mutator, "mutator must not be null");
    return updateIfEligible(
        authenticatedSessionId,
        AuthenticatedSession::isUsable,
        (session, now) -> mutator.apply(session),
        session -> null,
        correlationId)
        .map(result -> result.outcome() == AuthenticatedSessionConcurrencyOutcome.UNCHANGED
            ? AuthenticatedSessionConcurrencyResult.of(
                AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE)
            : result);
  }

  /**
   * Reloads and reevaluates eligibility and mutation on each genuine revision conflict.
   * An ineligible session is observed without a write. A null expiry deadline preserves
   * PTTL inside Redis; otherwise Redis validates and applies that absolute deadline.
   */
  public Mono<AuthenticatedSessionConcurrencyResult> updateIfEligible(
      AuthenticatedSessionId authenticatedSessionId,
      BiPredicate<AuthenticatedSession, Instant> eligibility,
      BiFunction<AuthenticatedSession, Instant, AuthenticatedSession> mutator,
      Function<AuthenticatedSession, Instant> expiryDeadline,
      String correlationId) {
    Objects.requireNonNull(authenticatedSessionId, "authenticatedSessionId must not be null");
    Objects.requireNonNull(eligibility, "eligibility must not be null");
    Objects.requireNonNull(mutator, "mutator must not be null");
    Objects.requireNonNull(expiryDeadline, "expiryDeadline must not be null");
    return Mono.defer(() -> attemptOnce(authenticatedSessionId, eligibility, mutator, expiryDeadline, correlationId))
        .retryWhen(
            Retry.backoff(2, Duration.ofMillis(10))
                .maxBackoff(Duration.ofMillis(50))
                .filter(RevisionConflictSignal.class::isInstance))
        .onErrorResume(
            AuthenticatedSessionConcurrencyService::isExhaustedRevisionConflict,
            error ->
                Mono.just(
                    AuthenticatedSessionConcurrencyResult.of(
                        AuthenticatedSessionConcurrencyOutcome.REVISION_CONFLICT_EXHAUSTED)));
  }

  private Mono<AuthenticatedSessionConcurrencyResult> attemptOnce(
      AuthenticatedSessionId authenticatedSessionId,
      BiPredicate<AuthenticatedSession, Instant> eligibility,
      BiFunction<AuthenticatedSession, Instant, AuthenticatedSession> mutator,
      Function<AuthenticatedSession, Instant> expiryDeadline,
      String correlationId) {
    return sessions
        .load(new LoadAuthenticatedSessionCommand(authenticatedSessionId, correlationId))
        .flatMap(
            optional -> {
              if (optional.isEmpty()) {
                return Mono.just(
                    AuthenticatedSessionConcurrencyResult.of(
                        AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_FOUND));
              }
              ProtectedAuthenticatedSession expectedEnvelope = optional.get();
              AuthenticatedSession current = protector.unprotect(expectedEnvelope);
              Instant now = Instant.now(clock);
              if (!eligibility.test(current, now)) {
                return Mono.just(AuthenticatedSessionConcurrencyResult.unchanged(current));
              }
              AuthenticatedSession mutated = mutator.apply(current, now);
              SessionRevision freshRevision = revisions.generate();
              AuthenticatedSession replacement =
                  new AuthenticatedSession(
                      mutated.authenticatedSessionId(),
                      mutated.actorContext(),
                      mutated.accounts(),
                      mutated.status(),
                      mutated.csrfTokenHash(),
                      mutated.createdAt(),
                      mutated.lastAccessedAt(),
                      mutated.idleExpiresAt(),
                      mutated.absoluteExpiresAt(),
                      freshRevision,
                      mutated.fundPriceModeMetadata(),
                      mutated.featureState());
              ProtectedAuthenticatedSession replacementEnvelope = protector.protect(replacement);
              var command =
                  new AuthenticatedSessionCasUpdateCommand(
                      authenticatedSessionId,
                      expectedEnvelope.sessionRevision(),
                      replacementEnvelope,
                      freshRevision,
                      correlationId,
                      expiryDeadline.apply(replacement));
              return casPort
                  .updateIfCurrent(command)
                  .flatMap(
                      result -> {
                        switch (result.outcome()) {
                          case APPLIED:
                            return Mono.just(
                                AuthenticatedSessionConcurrencyResult.applied(replacement));
                          case REVISION_MISMATCH:
                            return Mono.error(new RevisionConflictSignal());
                          default:
                            return Mono.just(
                                AuthenticatedSessionConcurrencyResult.of(
                                    AuthenticatedSessionConcurrencyOutcome.SYSTEM_FAILURE));
                        }
                      });
            });
  }

  /**
   * Reactor's {@code Retry.backoff(...)} does not let the original filtered exception escape when
   * the retry budget is exhausted: it wraps it in its own {@link
   * reactor.core.Exceptions.RetryExhaustedException}, with the original demoted to the cause.
   * {@link Exceptions#isRetryExhausted(Throwable)} plus a cause check is the correct way to detect
   * "we retried a revision conflict until the budget ran out" -- checking the top-level exception's
   * type directly (as a first pass at this method did) never matches, because the top-level type is
   * always {@code RetryExhaustedException} on exhaustion, never {@link RevisionConflictSignal}
   * itself.
   */
  private static boolean isExhaustedRevisionConflict(Throwable error) {
    return Exceptions.isRetryExhausted(error) && error.getCause() instanceof RevisionConflictSignal;
  }

  /** Internal-only marker used to drive {@code retryWhen}; never escapes this class. */
  private static final class RevisionConflictSignal extends RuntimeException {
    RevisionConflictSignal() {
      super("authenticated session revision conflict (internal retry signal)", null, false, false);
    }
  }
}
