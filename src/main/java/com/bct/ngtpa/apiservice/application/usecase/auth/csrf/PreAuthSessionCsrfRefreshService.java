package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.RotateAuthFlowSessionCsrfCommand;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Maps the existing revision-aware pre-auth rotation boundary into stable typed outcomes. */
public final class PreAuthSessionCsrfRefreshService {
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

    public static Outcome success(String token) {
      return new Outcome(RefreshCsrfTokenStatus.SUCCESS, token);
    }

    public static Outcome failure(RefreshCsrfTokenStatus status) {
      return new Outcome(status, null);
    }
  }

  private final AuthFlowSessionCsrfService delegate;
  private final Clock clock;

  public PreAuthSessionCsrfRefreshService(AuthFlowSessionCsrfService delegate, Clock clock) {
    this.delegate = Objects.requireNonNull(delegate);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<Outcome> refresh(String rawSessionId, String correlationId) {
    final AuthFlowSessionId sessionId;
    try {
      sessionId = new AuthFlowSessionId(rawSessionId);
    } catch (RuntimeException exception) {
      return Mono.just(Outcome.failure(RefreshCsrfTokenStatus.SESSION_INVALID));
    }
    return delegate
        .rotateForSession(
            new RotateAuthFlowSessionCsrfCommand(sessionId, clock.instant(), 1, correlationId))
        .map(result -> Outcome.success(result.rawToken()))
        .onErrorResume(error -> Mono.just(Outcome.failure(classifyRotationFailure(error))));
  }

  static RefreshCsrfTokenStatus classifyRotationFailure(Throwable error) {
    String message = error.getMessage() == null ? "" : error.getMessage();
    if (message.equals("Auth flow session not available")) {
      return RefreshCsrfTokenStatus.SESSION_MISSING;
    }
    if (message.equals("Auth flow session is not usable")) {
      return RefreshCsrfTokenStatus.SESSION_NOT_USABLE;
    }
    Throwable current = error;
    while (current != null) {
      String type = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
      String currentMessage =
          current.getMessage() == null ? "" : current.getMessage().toLowerCase(Locale.ROOT);
      if (type.contains("conflict")
          || type.contains("revision")
          || currentMessage.contains("revision conflict")
          || currentMessage.contains("concurrent mutation")) {
        return RefreshCsrfTokenStatus.CONFLICT;
      }
      current = current.getCause();
    }
    return RefreshCsrfTokenStatus.SYSTEM_FAILURE;
  }
}
