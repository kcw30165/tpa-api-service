package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationIssuanceResult.Status.*;

import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.port.in.IssuePasswordValidationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.PasswordCheckPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.session.RevokeAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.RevokeActiveAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class PasswordValidationIssuanceService implements IssuePasswordValidationUseCase {
  private final PasswordValidationThrottleService throttle;
  private final PasswordCheckPort passwords;
  private final PasswordValidationGrantService grants;
  private final RevokeAuthenticatedSessionUseCase revocation;
  private final Clock clock;
  private final FailureEventPort failures;

  public PasswordValidationIssuanceService(
      PasswordValidationThrottleService throttle,
      PasswordCheckPort passwords,
      PasswordValidationGrantService grants,
      RevokeAuthenticatedSessionUseCase revocation,
      Clock clock,
      FailureEventPort failures) {
    this.throttle = Objects.requireNonNull(throttle);
    this.passwords = Objects.requireNonNull(passwords);
    this.grants = Objects.requireNonNull(grants);
    this.revocation = Objects.requireNonNull(revocation);
    this.clock = Objects.requireNonNull(clock);
    this.failures = Objects.requireNonNull(failures);
  }

  @Override
  public Mono<PasswordValidationIssuanceResult> issue(PasswordValidationIssuanceCommand command) {
    return Mono.defer(() -> throttle.check(command.context().actor(), command.correlationId()))
        .flatMap(
            decision ->
                switch (decision.status()) {
                  case BLOCKED ->
                      Mono.just(
                          new PasswordValidationIssuanceResult(
                              THROTTLED, null, 0, decision.retryAfterSeconds()));
                  case UNAVAILABLE -> Mono.just(PasswordValidationIssuanceResult.of(UNAVAILABLE));
                  case ADMITTED -> checkPassword(command);
                })
        .defaultIfEmpty(PasswordValidationIssuanceResult.of(UNAVAILABLE))
        .onErrorReturn(PasswordValidationIssuanceResult.of(UNAVAILABLE));
  }

  private Mono<PasswordValidationIssuanceResult> checkPassword(
      PasswordValidationIssuanceCommand command) {
    var actor = command.context().actor();
    return Mono.defer(
            () ->
                passwords.check(
                    new PasswordCheckCommand(
                        actor.actorUserId(), command.password(), actor.actorUserRole())))
        .flatMap(
            check ->
                switch (check) {
                  case CORRECT ->
                      grants
                          .issue(
                              command.sessionId(),
                              new AuthenticatedAccountRef(command.context().account().accountRef()),
                              command.purpose(),
                              command.binding(),
                              check,
                              command.correlationId())
                          .map(this::issued);
                  case INCORRECT -> Mono.just(PasswordValidationIssuanceResult.of(INCORRECT));
                  case ACCOUNT_LOCKED -> invalidate(command);
                  case CUSTOMER_NOT_REGISTERED, UNAVAILABLE ->
                      Mono.just(PasswordValidationIssuanceResult.of(UNAVAILABLE));
                });
  }

  private PasswordValidationIssuanceResult issued(
      PasswordValidationGrantService.IssueResult result) {
    if (result.status() == PasswordValidationGrantService.Status.SESSION_NOT_USABLE) {
      return PasswordValidationIssuanceResult.of(AUTHENTICATION_REQUIRED);
    }
    if (result.status() != PasswordValidationGrantService.Status.ISSUED) {
      return PasswordValidationIssuanceResult.of(UNAVAILABLE);
    }
    long remaining = Duration.between(clock.instant(), result.expiresAt()).toSeconds();
    if (remaining <= 0) return PasswordValidationIssuanceResult.of(UNAVAILABLE);
    return new PasswordValidationIssuanceResult(
        ISSUED, result.token(), (int) Math.min(300, remaining), 0);
  }

  private Mono<PasswordValidationIssuanceResult> invalidate(
      PasswordValidationIssuanceCommand command) {
    return Mono.defer(
            () ->
                revocation.revoke(
                    new RevokeActiveAuthenticatedSessionCommand(
                        command.sessionId(),
                        "PASSWORD_VALIDATION_ACCOUNT_LOCKED",
                        command.correlationId())))
        .map(
            result ->
                switch (result.status()) {
                  case LOGGED_OUT, REVOKED, EXPIRED, NOT_FOUND ->
                      PasswordValidationIssuanceResult.of(LOCKED);
                  case ACTIVE, FAILED -> PasswordValidationIssuanceResult.of(LOCK_CLEANUP_FAILED);
                })
        .defaultIfEmpty(PasswordValidationIssuanceResult.of(LOCK_CLEANUP_FAILED))
        .onErrorReturn(PasswordValidationIssuanceResult.of(LOCK_CLEANUP_FAILED))
        .doOnNext(
            result -> {
              if (result.status() == LOCK_CLEANUP_FAILED) {
                publishInvalidationFailure(command.correlationId());
              }
            });
  }

  private void publishInvalidationFailure(String correlationId) {
    try {
      failures.publish(
          new FailureEvent(
              "password-validation.invalidation.failed",
              correlationId,
              "password-validation",
              "issue",
              FailureOrigin.LOCAL_SYSTEM,
              "SESSION_INVALIDATION_FAILED",
              ErrorCodes.PASSWORD_VALIDATION_UNAVAILABLE,
              503,
              false,
              Map.of()));
    } catch (RuntimeException ignored) {
      // Telemetry must not prevent the caller from clearing the session cookie.
    }
  }
}
