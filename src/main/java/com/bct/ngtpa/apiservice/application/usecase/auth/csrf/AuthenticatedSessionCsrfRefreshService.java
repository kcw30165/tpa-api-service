package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCsrfRotationStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.LoadAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.RotateAuthenticatedSessionCsrfHashCommand;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCsrfRotationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Validates without touching, then atomically rotates the authenticated CSRF hash. */
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
  private final AuthenticatedSessionCsrfRotationPort rotations;
  private final CsrfTokenGenerator tokenGenerator;
  private final CsrfTokenHasher tokenHasher;
  private final Clock clock;

  public AuthenticatedSessionCsrfRefreshService(
      AuthenticatedSessionPort sessions,
      AuthenticatedSessionProtectorPort protector,
      AuthenticatedSessionCsrfRotationPort rotations,
      CsrfTokenGenerator tokenGenerator,
      CsrfTokenHasher tokenHasher,
      Clock clock) {
    this.sessions = Objects.requireNonNull(sessions);
    this.protector = Objects.requireNonNull(protector);
    this.rotations = Objects.requireNonNull(rotations);
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
                    .map(protectedSession -> rotate(protectedSession, correlationId))
                    .orElseGet(
                        () -> Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_MISSING))))
        .onErrorReturn(Outcome.failure(RefreshCsrfTokenStatus.SYSTEM_FAILURE));
  }

  private Mono<Outcome> rotate(
      ProtectedAuthenticatedSession expectedProtectedSession, String correlationId) {
    AuthenticatedSession current;
    try {
      current = protector.unprotect(expectedProtectedSession);
    } catch (RuntimeException exception) {
      return Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_INVALID));
    }

    Instant now = clock.instant();
    if (!current.isUsable(now)) {
      return Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_NOT_USABLE));
    }

    String rawToken = tokenGenerator.generate();
    String replacementHash = tokenHasher.hash(rawToken);
    AuthenticatedSession replacement =
        new AuthenticatedSession(
            current.authenticatedSessionId(),
            current.actorContext(),
            current.accounts(),
            current.status(),
            replacementHash,
            current.createdAt(),
            current.lastAccessedAt(),
            current.idleExpiresAt(),
            current.absoluteExpiresAt());

    Instant effectiveExpiry =
        current.idleExpiresAt().isBefore(current.absoluteExpiresAt())
            ? current.idleExpiresAt()
            : current.absoluteExpiresAt();
    Duration remainingTtl = Duration.between(now, effectiveExpiry);
    if (remainingTtl.isZero() || remainingTtl.isNegative()) {
      return Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_NOT_USABLE));
    }

    return rotations
        .rotateCsrfHashIfCurrent(
            new RotateAuthenticatedSessionCsrfHashCommand(
                current.authenticatedSessionId(),
                expectedProtectedSession,
                protector.protect(replacement),
                current.csrfTokenHash(),
                replacementHash,
                remainingTtl,
                correlationId))
        .map(
            result -> {
              AuthenticatedSessionCsrfRotationStatus status = result.status();
              return switch (status) {
                case ROTATED -> Outcome.success(rawToken);
                case CONFLICT -> Outcome.failure(RefreshCsrfTokenStatus.CONFLICT);
                case NOT_FOUND -> Outcome.failure(RefreshCsrfTokenStatus.SESSION_MISSING);
                case EXPIRED -> Outcome.failure(RefreshCsrfTokenStatus.SESSION_NOT_USABLE);
                case SYSTEM_FAILURE -> Outcome.failure(RefreshCsrfTokenStatus.SYSTEM_FAILURE);
              };
            })
        .onErrorReturn(Outcome.failure(RefreshCsrfTokenStatus.SYSTEM_FAILURE));
  }
}
