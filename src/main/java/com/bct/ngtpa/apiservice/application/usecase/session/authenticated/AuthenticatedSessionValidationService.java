package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class AuthenticatedSessionValidationService
    implements ValidateAuthenticatedSessionUseCase {
  private static final Logger LOG =
      LoggerFactory.getLogger(AuthenticatedSessionValidationService.class);

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
                LOG.warn(
                    "event=authenticated.context.failure stage=session-validation reason=SESSION_NOT_ACTIVE lifecycleStatus={} sessionPresent={} correlationId={}",
                    result.status(),
                    result.session() != null,
                    command.correlationId());
                return Mono.error(new AuthenticatedPortalContextResolutionException());
              }
              if (!csrfTokenValidationService
                  .validate(command.submittedCsrfToken(), result.session().csrfTokenHash())
                  .valid()) {
                LOG.warn(
                    "event=authenticated.context.failure stage=session-validation reason=CSRF_MISMATCH correlationId={}",
                    command.correlationId());
                return Mono.error(new AuthenticatedCsrfValidationException());
              }
              return Mono.just(result.session());
            })
        .doOnError(
            error -> {
              if (!(error instanceof AuthenticatedPortalContextResolutionException)
                  && !(error instanceof AuthenticatedCsrfValidationException)) {
                LOG.error(
                    "event=authenticated.context.failure stage=session-validation reason=UNEXPECTED_ERROR exceptionType={} correlationId={}",
                    error.getClass().getSimpleName(),
                    command.correlationId());
              }
            })
        .onErrorMap(
            error ->
                error instanceof AuthenticatedPortalContextResolutionException
                        || error instanceof AuthenticatedCsrfValidationException
                    ? error
                    : new AuthenticatedPortalContextResolutionException());
  }
}
