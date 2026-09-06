package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayCommitRef;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict safe Login Start snapshot codec backed by the existing protected replay store. */
public final class LoginStartReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private static final int SCHEMA_VERSION = 1;
  private final LoginResponseEnvelope envelope;
  private final LoginStartReplayPort replayPort;
  private final LoginStartReplayProtectorPort protector;
  private final Clock clock;

  public LoginStartReplayCodec(
      LoginResponseEnvelope envelope,
      LoginStartReplayPort replayPort,
      LoginStartReplayProtectorPort protector,
      Clock clock) {
    this.envelope = Objects.requireNonNull(envelope);
    this.replayPort = Objects.requireNonNull(replayPort);
    this.protector = Objects.requireNonNull(protector);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<IdempotentCapturedResponse> capture(
      StartLoginResult result, HydratedIdempotentResponse transientResponse) {
    Objects.requireNonNull(result, "Login Start result must not be null");
    Objects.requireNonNull(transientResponse, "Login Start transient response must not be null");
    validateCapturable(result.status());
    if (transientResponse.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(transientResponse.mediaType())) {
      throw new IllegalArgumentException("Login Start transient transport mismatch");
    }
    String safeBody =
        envelope.encode(
            LoginIdempotencyAction.CREDENTIALS,
            new SafePayload(result.status().name(), result.failCount()));
    ProtectedReplayReference protectedReplayReference = referenceFor(result);
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            SCHEMA_VERSION,
            transientResponse.httpStatus(),
            MEDIA_TYPE,
            safeBody,
            transientResponse.safeHeaders(),
            protectedReplayReference);
    return Mono.just(
        new IdempotentCapturedResponse(
            protectedReplayReference == null
                ? transientResponse
                : transientResponse.withProtectedReplayReference(protectedReplayReference),
            snapshot));
  }

  public Mono<StartLoginResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "Login Start snapshot must not be null");
    if (snapshot.schemaVersion() != SCHEMA_VERSION) {
      return Mono.error(
          new IllegalArgumentException("Login Start replay schema version is unsupported"));
    }
    if (!MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())) {
      return Mono.error(new IllegalArgumentException("Login Start replay media type is invalid"));
    }
    SafePayload payload =
        envelope.decodePayload(
            LoginIdempotencyAction.CREDENTIALS,
            snapshot.serializedSafeBody(),
            SafePayload.class);
    StartLoginStatus status = status(payload.status());
    validateCapturable(status);
    if (snapshot.httpStatus() != httpStatus(status)) {
      return Mono.error(new IllegalArgumentException("Login Start replay HTTP status mismatch"));
    }
    if (status == StartLoginStatus.STARTED) {
      return hydrateStarted(snapshot, payload);
    }
    if (snapshot.protectedReplayReference() != null) {
      return Mono.error(
          new IllegalArgumentException("Login Start rejection cannot carry protected replay"));
    }
    return Mono.just(
        new StartLoginResult(status, null, null, null, List.of(), payload.failCount(), null));
  }

  private Mono<StartLoginResult> hydrateStarted(
      IdempotentTerminalResponseSnapshot snapshot, SafePayload payload) {
    if (snapshot.protectedReplayReference() == null) {
      return Mono.error(new IllegalArgumentException("Login Start protected replay is required"));
    }
    LoginStartReplayCommitRef ref;
    try {
      ref = new LoginStartReplayCommitRef(snapshot.protectedReplayReference().value());
    } catch (IllegalArgumentException exception) {
      return Mono.error(
          new IllegalArgumentException("Login Start protected replay reference is invalid"));
    }
    return replayPort
        .load(ref)
        .flatMap(
            stored ->
                stored
                    .map(protector::unprotect)
                    .filter(value -> value.expiresAt().isAfter(clock.instant()))
                    .map(
                        value -> {
                          long remaining =
                              Duration.between(clock.instant(), value.expiresAt()).toSeconds();
                          if (remaining <= 0) {
                            throw new IllegalArgumentException(
                                "Login Start protected replay is expired");
                          }
                          return Mono.just(
                              new StartLoginResult(
                                  StartLoginStatus.STARTED,
                                  value.authFlowSessionId(),
                                  value.csrfToken(),
                                  Math.toIntExact(Math.min(remaining, value.expiresInSeconds())),
                                  value.destinations(),
                                  payload.failCount(),
                                  ref.value()));
                        })
                    .orElseGet(
                        () ->
                            Mono.error(
                                new IllegalArgumentException(
                                    "Login Start protected replay is unavailable"))));
  }

  private ProtectedReplayReference referenceFor(StartLoginResult result) {
    if (result.status() != StartLoginStatus.STARTED) {
      if (result.replayCommitRef() != null) {
        throw new IllegalArgumentException("Login Start rejection cannot carry replay reference");
      }
      return null;
    }
    if (result.replayCommitRef() == null || result.replayCommitRef().isBlank()) {
      throw new IllegalArgumentException("Login Start success requires replay reference");
    }
    new LoginStartReplayCommitRef(result.replayCommitRef());
    return new ProtectedReplayReference(result.replayCommitRef());
  }

  private StartLoginStatus status(String value) {
    try {
      return StartLoginStatus.valueOf(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login Start replay status is unsupported");
    }
  }

  private void validateCapturable(StartLoginStatus status) {
    if (status == null
        || !(status == StartLoginStatus.STARTED
            || status == StartLoginStatus.INCORRECT_PASSWORD
            || status == StartLoginStatus.INCORRECT_USERNAME
            || status == StartLoginStatus.INVALID_ACCOUNT
            || status == StartLoginStatus.ACCOUNT_LOCKED)) {
      throw new IllegalArgumentException("Login Start result is not capturable");
    }
  }

  public static int httpStatus(StartLoginStatus status) {
    return switch (status) {
      case STARTED -> 200;
      case INCORRECT_PASSWORD, INCORRECT_USERNAME, INVALID_ACCOUNT -> 401;
      case ACCOUNT_LOCKED -> 423;
      default -> throw new IllegalArgumentException("Login Start result is not capturable");
    };
  }

  public record SafePayload(String status, Integer failCount) {
    public SafePayload {
      if (status == null || status.isBlank()) {
        throw new IllegalArgumentException("Login Start safe status must not be blank");
      }
    }
  }
}
