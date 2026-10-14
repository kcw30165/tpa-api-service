package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.LoadAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.Clock;
import java.util.Objects;
import java.util.function.UnaryOperator;
import reactor.core.publisher.Mono;

/**
 * Validates the authenticated session, then replaces only its CSRF hash through the revision-based
 * compare-and-set of {@link AuthenticatedSessionConcurrencyService}.
 *
 * <p>The compare-and-set checks the stored {@code sessionRevision}, not the whole stored value, so
 * a record whose protected attributes were serialized in another order is still updated. Lifecycle
 * timestamps, expiry and the remaining Redis TTL are preserved. The raw token is returned only
 * after the write is applied.
 */
public final class AuthenticatedSessionCsrfRefreshService {
  public record Outcome(RefreshCsrfTokenStatus status, String csrfToken) {
    public Outcome {
      Objects.requireNonNull(status, "status must not be null");
      if (status == RefreshCsrfTokenStatus.SUCCESS) {
        if (csrfToken == null || csrfToken.isBlank()) {
          throw new IllegalArgumentException("csrfToken must not be blank on success");
        }
      } else if (csrfToken != null) {
        throw new IllegalArgumentException("failure outcome must not contain a token");
      }
    }

    public static Outcome success(String csrfToken) {
      return new Outcome(RefreshCsrfTokenStatus.SUCCESS, csrfToken);
    }

    public static Outcome failure(RefreshCsrfTokenStatus status) {
      if (status == RefreshCsrfTokenStatus.SUCCESS) {
        throw new IllegalArgumentException("SUCCESS requires a token");
      }
      return new Outcome(status, null);
    }

    @Override
    public String toString() {
      return "Outcome[status="
          + status
          + ", csrfToken="
          + (csrfToken == null ? null : "<redacted>")
          + "]";
    }
  }

  private final AuthenticatedSessionPort sessions;
  private final AuthenticatedSessionProtectorPort protector;
  private final AuthenticatedSessionConcurrencyService concurrency;
  private final CsrfTokenGenerator tokenGenerator;
  private final CsrfTokenHasher tokenHasher;
  private final Clock clock;

  public AuthenticatedSessionCsrfRefreshService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionConcurrencyService concurrency,
      CsrfTokenGenerator tokenGenerator,
      CsrfTokenHasher tokenHasher,
      Clock clock) {
    this.sessions = Objects.requireNonNull(sessions);
    this.protector = Objects.requireNonNull(protector);
    this.concurrency = Objects.requireNonNull(concurrency);
    this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
    this.tokenHasher = Objects.requireNonNull(tokenHasher);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<Outcome> refresh(String rawSessionId, String correlationId) {
    AuthenticatedSessionId sessionId;
    try {
      sessionId = new AuthenticatedSessionId(rawSessionId);
    } catch (RuntimeException exception) {
      return Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_INVALID));
    }

    return sessions
        .load(new LoadAuthenticatedSessionCommand(sessionId, correlationId))
        .flatMap(
            optional ->
                optional
                    .map(envelope -> rotate(sessionId, envelope, correlationId))
                    .orElseGet(
                        () -> Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_MISSING))))
        .onErrorReturn(Outcome.failure(RefreshCsrfTokenStatus.SYSTEM_FAILURE));
  }

  private Mono<Outcome> rotate(
      AuthenticatedSessionId sessionId,
      ProtectedAuthenticatedSession envelope,
      String correlationId) {
    AuthenticatedSession current;
    try {
      current = protector.unprotect(envelope);
    } catch (RuntimeException exception) {
      return Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_INVALID));
    }
    if (!current.isUsable(clock.instant())) {
      return Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_NOT_USABLE));
    }

    String rawToken = tokenGenerator.generate();
    String replacementHash = tokenHasher.hash(rawToken);
    return concurrency
        .updateIfUsable(sessionId, withCsrfHash(replacementHash), correlationId)
        .map(result -> toOutcome(result, rawToken));
  }

  private static UnaryOperator<AuthenticatedSession> withCsrfHash(String csrfTokenHash) {
    return session ->
        new AuthenticatedSession(
            session.authenticatedSessionId(),
            session.actorContext(),
            session.accounts(),
            session.status(),
            csrfTokenHash,
            session.createdAt(),
            session.lastAccessedAt(),
            session.idleExpiresAt(),
            session.absoluteExpiresAt(),
            session.revision(),
            session.fundPriceModeMetadata());
  }

  private static Outcome toOutcome(AuthenticatedSessionConcurrencyResult result, String rawToken) {
    return switch (result.outcome()) {
      case APPLIED -> Outcome.success(rawToken);
      case SESSION_NOT_FOUND -> Outcome.failure(RefreshCsrfTokenStatus.SESSION_MISSING);
      case SESSION_NOT_USABLE -> Outcome.failure(RefreshCsrfTokenStatus.SESSION_NOT_USABLE);
      case REVISION_CONFLICT_EXHAUSTED -> Outcome.failure(RefreshCsrfTokenStatus.CONFLICT);
      case SYSTEM_FAILURE -> Outcome.failure(RefreshCsrfTokenStatus.SYSTEM_FAILURE);
    };
  }
}
