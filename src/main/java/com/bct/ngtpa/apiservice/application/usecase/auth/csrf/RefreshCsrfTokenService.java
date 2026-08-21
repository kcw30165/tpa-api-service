package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenResult;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCsrfTokenUseCase;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Selects authenticated context first and never downgrades to pre-auth. */
public final class RefreshCsrfTokenService implements RefreshCsrfTokenUseCase {
  private final AuthenticatedSessionCsrfRefreshService authenticatedRefresh;
  private final PreAuthSessionCsrfRefreshService preAuthRefresh;
  private final OriginRefererValidator provenanceValidator;
  private final List<String> allowedOrigins;
  private final Clock clock;

  public RefreshCsrfTokenService(
      AuthenticatedSessionCsrfRefreshService authenticatedRefresh,
      PreAuthSessionCsrfRefreshService preAuthRefresh,
      OriginRefererValidator provenanceValidator,
      Collection<String> allowedOrigins,
      Clock clock) {
    this.authenticatedRefresh = Objects.requireNonNull(authenticatedRefresh);
    this.preAuthRefresh = Objects.requireNonNull(preAuthRefresh);
    this.provenanceValidator = Objects.requireNonNull(provenanceValidator);
    this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public Mono<RefreshCsrfTokenResult> refresh(RefreshCsrfTokenCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!provenanceValidator
        .validate(command.origin(), command.referer(), allowedOrigins)
        .valid()) {
      return Mono.just(RefreshCsrfTokenResult.failure(RefreshCsrfTokenStatus.ORIGIN_REJECTED));
    }

    if (hasText(command.authenticatedSessionId())) {
      return authenticatedRefresh
          .refresh(command.authenticatedSessionId(), command.requestId())
          .map(
              outcome ->
                  outcome.status() == RefreshCsrfTokenStatus.SUCCESS
                      ? RefreshCsrfTokenResult.success(
                          CsrfRefreshSessionType.AUTHENTICATED, outcome.csrfToken())
                      : RefreshCsrfTokenResult.failure(outcome.status()));
    }

    if (!hasText(command.authFlowSessionId())) {
      return Mono.just(RefreshCsrfTokenResult.failure(RefreshCsrfTokenStatus.SESSION_MISSING));
    }

    return preAuthRefresh
        .refresh(command.authFlowSessionId(), command.requestId())
        .map(
            outcome ->
                outcome.status() == RefreshCsrfTokenStatus.SUCCESS
                    ? RefreshCsrfTokenResult.success(
                        CsrfRefreshSessionType.PRE_AUTH, outcome.csrfToken())
                    : RefreshCsrfTokenResult.failure(outcome.status()));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
