package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class AuthenticatedSessionValidationService
    implements ValidateAuthenticatedSessionUseCase {
  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle;
  private final CsrfTokenValidationService csrfTokenValidationService;

  public AuthenticatedSessionValidationService(
      LoadAndTouchAuthenticatedSessionUseCase lifecycle, CsrfTokenHasher csrfTokenHasher) {
    this.lifecycle = Objects.requireNonNull(lifecycle);
    this.csrfTokenValidationService =
        new CsrfTokenValidationService(
            Objects.requireNonNull(csrfTokenHasher, "csrfTokenHasher must not be null"));
  }

  @Override
  public Mono<com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession>
      validate(ValidateAuthenticatedSessionCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return lifecycle
        .loadAndTouch(
            new LoadAndTouchAuthenticatedSessionCommand(
                command.authenticatedSessionId(), command.correlationId()))
        .flatMap(
            result -> {
              if (result.status() != AuthenticatedSessionLifecycleStatus.ACTIVE
                  || result.session() == null) {
                return Mono.error(new AuthenticatedPortalContextResolutionException());
              }
              if (!csrfTokenValidationService
                  .validate(command.submittedCsrfToken(), result.session().csrfTokenHash())
                  .valid()) {
                return Mono.error(new AuthenticatedCsrfValidationException());
              }
              return Mono.just(result.session());
            })
        .onErrorMap(
            error ->
                error instanceof AuthenticatedPortalContextResolutionException
                        || error instanceof AuthenticatedCsrfValidationException
                    ? error
                    : new AuthenticatedPortalContextResolutionException());
  }
}
