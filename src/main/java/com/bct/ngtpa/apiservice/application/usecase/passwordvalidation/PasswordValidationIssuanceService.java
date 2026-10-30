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
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

public final class PasswordValidationIssuanceService implements IssuePasswordValidationUseCase {
  private static final String DOWNSTREAM_UNAVAILABLE = "DOWNSTREAM_UNAVAILABLE";
  private static final String REVOCATION_FAILED = "REVOCATION_FAILED";
  private static final int DEFAULT_TTL_SECONDS = PasswordValidationGrantService.DEFAULT_TTL_SECONDS;
  private final PasswordValidationThrottleService throttle;
  private final PasswordCheckPort passwords;
  private final PasswordValidationGrantService grants;
  private final RevokeAuthenticatedSessionUseCase revocation;
  private final Clock clock;
  private final FailureEventPort failures;
  private final int ttlSeconds;

  public PasswordValidationIssuanceService(
      PasswordValidationThrottleService throttle,
      PasswordCheckPort passwords,
      PasswordValidationGrantService grants,
      RevokeAuthenticatedSessionUseCase revocation,
      Clock clock,
      FailureEventPort failures) {
    this(throttle, passwords, grants, revocation, clock, failures, DEFAULT_TTL_SECONDS);
  }

  public PasswordValidationIssuanceService(
      PasswordValidationThrottleService throttle,
      PasswordCheckPort passwords,
      PasswordValidationGrantService grants,
      RevokeAuthenticatedSessionUseCase revocation,
      Clock clock,
      FailureEventPort failures,
      int ttlSeconds) {
    this.throttle = Objects.requireNonNull(throttle);
    this.passwords = Objects.requireNonNull(passwords);
    this.grants = Objects.requireNonNull(grants);
    this.revocation = Objects.requireNonNull(revocation);
    this.clock = Objects.requireNonNull(clock);
    this.failures = Objects.requireNonNull(failures);
    if (ttlSeconds < 30 || ttlSeconds > 900) {
      throw new IllegalArgumentException(
          "password-validation.grant.ttl-seconds must be between 30 and 900");
    }
    this.ttlSeconds = ttlSeconds;
  }

  @Override
  public Mono<PasswordValidationIssuanceResult> issue(PasswordValidationIssuanceCommand command) {
    return Mono.defer(
        () -> {
          AtomicReference<String> reason = new AtomicReference<>(DOWNSTREAM_UNAVAILABLE);
          return throttle
              .check(command.context().actor(), command.correlationId())
              .flatMap(
                  decision ->
                      switch (decision.status()) {
                        case BLOCKED -> {
                          reason.set("THROTTLED");
                          yield Mono.just(
                              new PasswordValidationIssuanceResult(
                                  THROTTLED, null, 0, decision.retryAfterSeconds()));
                        }
                        case UNAVAILABLE ->
                            Mono.just(PasswordValidationIssuanceResult.of(UNAVAILABLE));
                        case ADMITTED -> checkPassword(command, reason);
                      })
              .defaultIfEmpty(PasswordValidationIssuanceResult.of(UNAVAILABLE))
              .onErrorResume(
                  failure -> {
                    reason.set(DOWNSTREAM_UNAVAILABLE);
                    return Mono.just(PasswordValidationIssuanceResult.of(UNAVAILABLE));
                  })
              .doOnNext(result -> publishFailure(command, reason.get(), result));
        });
  }

  private Mono<PasswordValidationIssuanceResult> checkPassword(
      PasswordValidationIssuanceCommand command, AtomicReference<String> reason) {
    var actor = command.context().actor();
    var account = command.context().account();
    if (account == null || account.accountEnv() == null || account.accountEnv().isBlank()) {
      reason.set("ACCOUNT_ENV_MISSING");
      return Mono.just(PasswordValidationIssuanceResult.of(UNAVAILABLE));
    }
    return Mono.defer(
            () ->
                passwords.check(
                    new PasswordCheckCommand(
                        actor.actorUserId(),
                        command.password(),
                        actor.actorUserRole(),
                        account.accountEnv())))
        .flatMap(
            check ->
                switch (check) {
                  case CORRECT ->
                      grants
                          .issue(
                              command.sessionId(),
                              new AuthenticatedAccountRef(account.accountRef()),
                              command.purpose(),
                              command.binding(),
                              check,
                              command.correlationId())
                          .map(
                              result -> {
                                if (result.status()
                                    == PasswordValidationGrantService.Status.SESSION_NOT_USABLE) {
                                  reason.set("SESSION_NOT_USABLE");
                                } else if (result.status()
                                    != PasswordValidationGrantService.Status.ISSUED) {
                                  reason.set("GRANT_NOT_ISSUED");
                                }
                                var issued = issued(result);
                                if (issued.status() == UNAVAILABLE) {
                                  reason.set("GRANT_NOT_ISSUED");
                                }
                                return issued;
                              });
                  case INCORRECT -> {
                    reason.set("PASSWORD_INCORRECT");
                    yield Mono.just(PasswordValidationIssuanceResult.of(INCORRECT));
                  }
                  case ACCOUNT_LOCKED -> invalidate(command, reason);
                  case CUSTOMER_NOT_REGISTERED -> {
                    reason.set("CUSTOMER_NOT_REGISTERED");
                    yield Mono.just(PasswordValidationIssuanceResult.of(UNAVAILABLE));
                  }
                  case UNAVAILABLE -> {
                    reason.set(DOWNSTREAM_UNAVAILABLE);
                    yield Mono.just(PasswordValidationIssuanceResult.of(UNAVAILABLE));
                  }
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
        ISSUED, result.token(), (int) Math.min(ttlSeconds, remaining), 0);
  }

  private Mono<PasswordValidationIssuanceResult> invalidate(
      PasswordValidationIssuanceCommand command, AtomicReference<String> reason) {
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
                  case LOGGED_OUT, REVOKED, EXPIRED, NOT_FOUND -> {
                    reason.set("ACCOUNT_LOCKED");
                    yield PasswordValidationIssuanceResult.of(LOCKED);
                  }
                  case ACTIVE, FAILED -> {
                    reason.set(REVOCATION_FAILED);
                    yield PasswordValidationIssuanceResult.of(LOCK_CLEANUP_FAILED);
                  }
                })
        .defaultIfEmpty(PasswordValidationIssuanceResult.of(LOCK_CLEANUP_FAILED))
        .doOnNext(
            result -> {
              if (result.status() == LOCK_CLEANUP_FAILED) {
                reason.set(REVOCATION_FAILED);
              }
            })
        .onErrorResume(
            failure -> {
              reason.set(REVOCATION_FAILED);
              return Mono.just(PasswordValidationIssuanceResult.of(LOCK_CLEANUP_FAILED));
            });
  }

  private void publishFailure(
      PasswordValidationIssuanceCommand command,
      String reason,
      PasswordValidationIssuanceResult result) {
    if (result.status() == ISSUED) {
      return;
    }
    FailureOrigin origin =
        switch (reason) {
          case "THROTTLED", "PASSWORD_INCORRECT", "ACCOUNT_LOCKED", "SESSION_NOT_USABLE" ->
              FailureOrigin.LOCAL_BUSINESS;
          case "CUSTOMER_NOT_REGISTERED" -> FailureOrigin.DOWNSTREAM_BUSINESS;
          default -> FailureOrigin.LOCAL_SYSTEM;
        };
    String event =
        reason.equals(REVOCATION_FAILED)
            ? "password-validation.invalidation.failed"
            : "password-validation.issue.rejected";
    try {
      failures.publish(
          new FailureEvent(
              event,
              command.correlationId(),
              "password-validation",
              "issue",
              origin,
              reason,
              null,
              httpStatus(result.status()),
              false,
              Map.of("stage", "issuance", "reason", reason)));
    } catch (RuntimeException ignored) {
      // Telemetry must not change the issuance response.
    }
  }

  private static int httpStatus(PasswordValidationIssuanceResult.Status status) {
    return switch (status) {
      case INCORRECT -> 401;
      case LOCKED -> 423;
      case THROTTLED -> 429;
      case AUTHENTICATION_REQUIRED -> 401;
      case ISSUED -> 200;
      case LOCK_CLEANUP_FAILED, UNAVAILABLE -> 503;
    };
  }
}
