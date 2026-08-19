package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyReservation;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Single-envelope cancellation retaining the protected context until the original TTL expires. */
public final class CancelUsernameRecoveryService implements CancelUsernameRecoveryUseCase {
  private final UsernameRecoveryCancelFingerprintGenerator fingerprintGenerator;
  private final UsernameRecoveryIdempotencyPort idempotencyPort;
  private final AuthFlowSessionPort authFlowSessionPort;
  private final UsernameRecoveryAuthFlowContextBinding contextBinding;
  private final OriginRefererValidator originRefererValidator = new OriginRefererValidator();
  private final CsrfTokenValidationService csrfTokenValidationService;
  private final List<String> allowedOrigins;
  private final Clock clock;
  private final int ttlSeconds;
  private final SecureRandom secureRandom = new SecureRandom();

  public CancelUsernameRecoveryService(
      UsernameRecoveryCancelFingerprintGenerator fingerprintGenerator,
      UsernameRecoveryIdempotencyPort idempotencyPort,
      AuthFlowSessionPort authFlowSessionPort,
      UsernameRecoveryAuthFlowContextBinding contextBinding,
      CsrfTokenValidationService csrfTokenValidationService,
      List<String> allowedOrigins,
      Clock clock,
      int ttlSeconds) {
    this.fingerprintGenerator = Objects.requireNonNull(fingerprintGenerator);
    this.idempotencyPort = Objects.requireNonNull(idempotencyPort);
    this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort);
    this.contextBinding = Objects.requireNonNull(contextBinding);
    this.csrfTokenValidationService = Objects.requireNonNull(csrfTokenValidationService);
    this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    this.clock = Objects.requireNonNull(clock);
    if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
    this.ttlSeconds = ttlSeconds;
  }

  @Override
  public Mono<UsernameRecoveryCancelResult> cancel(UsernameRecoveryCancelCommand command) {
    return Mono.error(UsernameRecoveryCancelException.securityContextRequired());
  }

  public Mono<UsernameRecoveryCancelResult> cancel(
      UsernameRecoveryCancelCommand command, String rawCsrfToken, String origin, String referer) {
    if (command == null) return Mono.error(UsernameRecoveryCancelException.validationFailed());
    final UsernameRecoveryIdempotencyKey key;
    try {
      key = new UsernameRecoveryIdempotencyKey(command.idempotencyKey());
    } catch (IllegalArgumentException error) {
      return Mono.error(UsernameRecoveryCancelException.validationFailed());
    }
    var fingerprint = fingerprintGenerator.derive(command);
    var now = clock.instant();
    var ttl = Duration.ofSeconds(ttlSeconds);
    var reserve =
        new UsernameRecoveryIdempotencyPort.ReserveCommand(
            UsernameRecoveryIdempotencyAction.CANCEL,
            key,
            fingerprint,
            generateOperationReference(),
            now,
            ttl);
    return idempotencyPort
        .reserve(reserve)
        .onErrorMap(Exception.class, CancelPersistenceException::new)
        .flatMap(
            result ->
                switch (result.outcome()) {
                  case RESERVED ->
                      proceed(
                          command,
                          rawCsrfToken,
                          origin,
                          referer,
                          key,
                          fingerprint,
                          result.reservation(),
                          now,
                          ttl);
                  case COMPLETED_REPLAY -> replay(key, command, now);
                  case DUPLICATE_IN_PROGRESS ->
                      Mono.error(UsernameRecoveryCancelException.idempotencyInProgress());
                  case KEY_REUSED_WITH_DIFFERENT_REQUEST ->
                      Mono.error(UsernameRecoveryCancelException.keyReused());
                  case UNKNOWN_OUTCOME_EXISTING ->
                      Mono.error(UsernameRecoveryCancelException.unknownOutcomeExisting());
                  case SYSTEM_FAILURE ->
                      Mono.error(UsernameRecoveryCancelException.systemFailure());
                  default ->
                      throw new IllegalStateException(
                          "unexpected idempotency reserve outcome: " + result.outcome());
                })
        .onErrorResume(
            CancelPersistenceException.class,
            error -> Mono.error(UsernameRecoveryCancelException.systemFailure(error)));
  }

  private Mono<UsernameRecoveryCancelResult> proceed(
      UsernameRecoveryCancelCommand command,
      String rawCsrfToken,
      String origin,
      String referer,
      UsernameRecoveryIdempotencyKey key,
      UsernameRecoveryIdempotencyFingerprint fingerprint,
      UsernameRecoveryIdempotencyReservation reservation,
      Instant now,
      Duration ttl) {
    return authFlowSessionPort
        .loadSnapshot(command.authFlowSessionId())
        .onErrorMap(Exception.class, CancelPersistenceException::new)
        .flatMap(
            optional ->
                optional
                    .map(
                        snapshot ->
                            handleEnvelope(
                                snapshot,
                                command,
                                rawCsrfToken,
                                origin,
                                referer,
                                key,
                                fingerprint,
                                reservation,
                                now,
                                ttl))
                    .orElseGet(() -> completeIdempotency(key, fingerprint, reservation, now, ttl)))
        .onErrorResume(
            CancelPersistenceException.class,
            error -> recordUnknownOutcome(key, fingerprint, reservation, now, ttl));
  }

  private Mono<UsernameRecoveryCancelResult> handleEnvelope(
      AuthFlowSessionSnapshot snapshot,
      UsernameRecoveryCancelCommand command,
      String rawCsrfToken,
      String origin,
      String referer,
      UsernameRecoveryIdempotencyKey key,
      UsernameRecoveryIdempotencyFingerprint fingerprint,
      UsernameRecoveryIdempotencyReservation reservation,
      Instant now,
      Duration ttl) {
    var session = snapshot.session();
    if (!session.authFlowSessionId().equals(command.authFlowSessionId())
        || session.scenario() != AuthFlowScenario.USERNAME_RECOVERY) {
      return Mono.error(UsernameRecoveryCancelException.systemFailure());
    }
    if (session.isExpired(now)) {
      return completeIdempotency(key, fingerprint, reservation, now, ttl);
    }
    if (session.status() == AuthFlowStatus.CANCELLED) {
      return validateRetainedContext(session)
          .then(completeIdempotency(key, fingerprint, reservation, now, ttl));
    }
    if (session.isTerminal()) {
      return completeIdempotency(key, fingerprint, reservation, now, ttl);
    }
    if (!originRefererAllowed(origin, referer)) {
      return Mono.error(UsernameRecoveryCancelException.securityInvalid());
    }
    try {
      if (!csrfTokenValidationService.validate(rawCsrfToken, session.csrfTokenHash()).valid()) {
        return Mono.error(UsernameRecoveryCancelException.securityInvalid());
      }
    } catch (RuntimeException error) {
      return Mono.error(UsernameRecoveryCancelException.systemFailure(error));
    }
    return validateRetainedContext(session)
        .then(
            Mono.defer(
                () ->
                    authFlowSessionPort
                        .cancelRevisioned(snapshot, now)
                        .onErrorMap(Exception.class, CancelPersistenceException::new)))
        .flatMap(
            result ->
                result.outcome() == AuthFlowSessionMutationOutcome.APPLIED
                    ? validateCancelledResult(
                        result.snapshot(), session, key, fingerprint, reservation, now, ttl)
                    : Mono.error(
                        new CancelPersistenceException(
                            new IllegalStateException(
                                "auth-flow cancel outcome uncertain: " + result.outcome()))));
  }

  private Mono<Void> validateRetainedContext(
      com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession session) {
    try {
      contextBinding.load(session);
      return Mono.empty();
    } catch (RuntimeException error) {
      return Mono.error(new CancelPersistenceException(error));
    }
  }

  private Mono<UsernameRecoveryCancelResult> validateCancelledResult(
      AuthFlowSessionSnapshot cancelled,
      com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession original,
      UsernameRecoveryIdempotencyKey key,
      UsernameRecoveryIdempotencyFingerprint fingerprint,
      UsernameRecoveryIdempotencyReservation reservation,
      Instant now,
      Duration ttl) {
    var session = cancelled == null ? null : cancelled.session();
    if (session == null
        || session.status() != AuthFlowStatus.CANCELLED
        || !session.createdAt().equals(original.createdAt())
        || !session.expiresAt().equals(original.expiresAt())
        || !session.data().equals(original.data())) {
      return Mono.error(
          new CancelPersistenceException(
              new IllegalStateException(
                  "cancelled envelope did not retain protected state and expiry")));
    }
    return completeIdempotency(key, fingerprint, reservation, now, ttl);
  }

  private Mono<UsernameRecoveryCancelResult> completeIdempotency(
      UsernameRecoveryIdempotencyKey key,
      UsernameRecoveryIdempotencyFingerprint fingerprint,
      UsernameRecoveryIdempotencyReservation reservation,
      Instant now,
      Duration ttl) {
    ReplaySnapshot replay = UsernameRecoveryCancelReplayCodec.encode();
    var command =
        new UsernameRecoveryIdempotencyPort.CompleteCommand(
            UsernameRecoveryIdempotencyAction.CANCEL,
            key,
            fingerprint,
            reservation.operationReference(),
            replay,
            now,
            ttl);
    return idempotencyPort
        .complete(command)
        .onErrorMap(Exception.class, CancelPersistenceException::new)
        .map(
            record ->
                new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS));
  }

  private Mono<UsernameRecoveryCancelResult> recordUnknownOutcome(
      UsernameRecoveryIdempotencyKey key,
      UsernameRecoveryIdempotencyFingerprint fingerprint,
      UsernameRecoveryIdempotencyReservation reservation,
      Instant now,
      Duration ttl) {
    var command =
        new UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand(
            UsernameRecoveryIdempotencyAction.CANCEL,
            key,
            fingerprint,
            reservation.operationReference(),
            now,
            ttl);
    return idempotencyPort
        .recordUnknownOutcome(command)
        .onErrorMap(Exception.class, CancelPersistenceException::new)
        .map(
            record ->
                new UsernameRecoveryCancelResult(
                    UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED));
  }

  private Mono<UsernameRecoveryCancelResult> replay(
      UsernameRecoveryIdempotencyKey key, UsernameRecoveryCancelCommand command, Instant now) {
    return idempotencyPort
        .resolve(UsernameRecoveryIdempotencyAction.CANCEL, key)
        .onErrorMap(Exception.class, CancelPersistenceException::new)
        .flatMap(
            optional -> {
              if (optional.isEmpty())
                return Mono.error(UsernameRecoveryCancelException.systemFailure());
              var record = optional.get();
              if (record.action() != UsernameRecoveryIdempotencyAction.CANCEL
                  || !record.idempotencyKey().equals(key)
                  || record.status() != UsernameRecoveryIdempotencyRecord.Status.COMPLETED
                  || record.replaySnapshot() == null
                  || !UsernameRecoveryCancelReplayCodec.decode(record.replaySnapshot()).decoded()) {
                return Mono.error(UsernameRecoveryCancelException.systemFailure());
              }
              return authFlowSessionPort
                  .loadSnapshot(command.authFlowSessionId())
                  .onErrorMap(Exception.class, CancelPersistenceException::new)
                  .flatMap(
                      envelope -> {
                        if (envelope.isEmpty()) {
                          return Mono.just(
                              new UsernameRecoveryCancelResult(
                                  UsernameRecoveryCancelResult.Outcome.SUCCESS));
                        }
                        var session = envelope.get().session();
                        if (session.scenario() != AuthFlowScenario.USERNAME_RECOVERY
                            || session.isExpired(now)) {
                          return Mono.error(UsernameRecoveryCancelException.systemFailure());
                        }
                        if (session.status() != AuthFlowStatus.CANCELLED) {
                          return Mono.error(UsernameRecoveryCancelException.systemFailure());
                        }
                        return validateRetainedContext(session)
                            .thenReturn(
                                new UsernameRecoveryCancelResult(
                                    UsernameRecoveryCancelResult.Outcome.SUCCESS));
                      });
            });
  }

  private boolean originRefererAllowed(String origin, String referer) {
    return allowedOrigins.isEmpty()
        || originRefererValidator.validate(origin, referer, allowedOrigins).valid();
  }

  private UsernameRecoveryIdempotencyOperationReference generateOperationReference() {
    byte[] bytes = new byte[18];
    secureRandom.nextBytes(bytes);
    return new UsernameRecoveryIdempotencyOperationReference(
        "op_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }

  private static final class CancelPersistenceException extends RuntimeException {
    private CancelPersistenceException(Throwable cause) {
      super("Username recovery cancel persistence failed", cause);
    }
  }
}
