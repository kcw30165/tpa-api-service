package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Idempotency-neutral, revision-aware acquired execution for Username Recovery CANCEL. */
public final class UsernameRecoveryCancelProtectedOperation {
  private final AuthFlowSessionPort authFlowSessionPort;
  private final UsernameRecoveryAuthFlowContextBinding contextBinding;
  private final CsrfTokenValidationService csrfTokenValidationService;
  private final OriginRefererValidator originRefererValidator = new OriginRefererValidator();
  private final List<String> allowedOrigins;

  public UsernameRecoveryCancelProtectedOperation(
      AuthFlowSessionPort authFlowSessionPort,
      UsernameRecoveryAuthFlowContextBinding contextBinding,
      CsrfTokenValidationService csrfTokenValidationService,
      List<String> allowedOrigins) {
    this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort);
    this.contextBinding = Objects.requireNonNull(contextBinding);
    this.csrfTokenValidationService = Objects.requireNonNull(csrfTokenValidationService);
    this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }

  public Mono<UsernameRecoveryCancelProtectedExecutionResult> execute(
      UsernameRecoveryCancelCommand command,
      String rawCsrfToken,
      String origin,
      String referer,
      Instant now) {
    if (command == null || now == null) {
      return Mono.error(UsernameRecoveryCancelException.validationFailed());
    }
    return authFlowSessionPort
        .loadSnapshot(command.authFlowSessionId())
        .flatMap(
            optional ->
                optional
                    .map(snapshot -> handle(snapshot, command, rawCsrfToken, origin, referer, now))
                    .orElseGet(() -> success()))
        .onErrorResume(UsernameRecoveryCancelException.class, Mono::error)
        .onErrorResume(
            error ->
                Mono.just(UsernameRecoveryCancelProtectedExecutionResult.provenPreDispatch(error)));
  }

  private Mono<UsernameRecoveryCancelProtectedExecutionResult> handle(
      AuthFlowSessionSnapshot snapshot,
      UsernameRecoveryCancelCommand command,
      String rawCsrfToken,
      String origin,
      String referer,
      Instant now) {
    AuthFlowSession session = snapshot.session();
    if (!session.authFlowSessionId().equals(command.authFlowSessionId())
        || session.scenario() != AuthFlowScenario.USERNAME_RECOVERY) {
      return Mono.error(UsernameRecoveryCancelException.systemFailure());
    }
    if (session.isExpired(now)
        || (session.isTerminal() && session.status() != AuthFlowStatus.CANCELLED)) {
      return success();
    }
    if (session.status() == AuthFlowStatus.CANCELLED) {
      return validateContext(session).then(success());
    }
    if (!allowedOrigins.isEmpty()
        && !originRefererValidator.validate(origin, referer, allowedOrigins).valid()) {
      return Mono.error(UsernameRecoveryCancelException.securityInvalid());
    }
    try {
      if (!csrfTokenValidationService.validate(rawCsrfToken, session.csrfTokenHash()).valid()) {
        return Mono.error(UsernameRecoveryCancelException.securityInvalid());
      }
      contextBinding.load(session);
    } catch (UsernameRecoveryCancelException exception) {
      return Mono.error(exception);
    } catch (RuntimeException exception) {
      return Mono.just(UsernameRecoveryCancelProtectedExecutionResult.provenPreDispatch(exception));
    }
    return authFlowSessionPort
        .cancelRevisioned(snapshot, now)
        .flatMap(
            result -> {
              if (result.outcome() != AuthFlowSessionMutationOutcome.APPLIED) {
                return Mono.just(
                    UsernameRecoveryCancelProtectedExecutionResult.possibleSideEffect(
                        new IllegalStateException("auth-flow cancel outcome uncertain")));
              }
              return validateCancelled(result.snapshot(), session);
            })
        .onErrorResume(
            error ->
                Mono.just(
                    UsernameRecoveryCancelProtectedExecutionResult.possibleSideEffect(error)));
  }

  private Mono<Void> validateContext(AuthFlowSession session) {
    return Mono.fromRunnable(() -> contextBinding.load(session));
  }

  private Mono<UsernameRecoveryCancelProtectedExecutionResult> validateCancelled(
      AuthFlowSessionSnapshot cancelled, AuthFlowSession original) {
    AuthFlowSession session = cancelled == null ? null : cancelled.session();
    if (session == null
        || session.status() != AuthFlowStatus.CANCELLED
        || !session.createdAt().equals(original.createdAt())
        || !session.expiresAt().equals(original.expiresAt())
        || !session.data().equals(original.data())) {
      return Mono.just(
          UsernameRecoveryCancelProtectedExecutionResult.possibleSideEffect(
              new IllegalStateException("cancelled envelope continuity is invalid")));
    }
    try {
      contextBinding.load(session);
      return success();
    } catch (RuntimeException exception) {
      return Mono.just(
          UsernameRecoveryCancelProtectedExecutionResult.possibleSideEffect(exception));
    }
  }

  private static Mono<UsernameRecoveryCancelProtectedExecutionResult> success() {
    return Mono.just(
        UsernameRecoveryCancelProtectedExecutionResult.completed(
            new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS)));
  }
}
