package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordError;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict safe START snapshot codec backed by separately protected workflow continuity. */
public final class ForgotPasswordStartReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private static final int SCHEMA_VERSION = 1;

  private final ActionTaggedIdempotencyResponseEnvelope envelope;
  private final ForgotPasswordStartProtectedReplayPort protectedReplay;
  private final CreateAuthFlowSessionCsrfUseCase csrf;
  private final Clock clock;

  public ForgotPasswordStartReplayCodec(
      ActionTaggedIdempotencyResponseEnvelope envelope,
      ForgotPasswordStartProtectedReplayPort protectedReplay,
      CreateAuthFlowSessionCsrfUseCase csrf,
      Clock clock) {
    this.envelope = Objects.requireNonNull(envelope);
    this.protectedReplay = Objects.requireNonNull(protectedReplay);
    this.csrf = Objects.requireNonNull(csrf);
    this.clock = Objects.requireNonNull(clock);
  }

  public Mono<IdempotentCapturedResponse> capture(
      StartForgotPasswordResult result,
      HydratedIdempotentResponse transientResponse,
      Duration ttl) {
    Objects.requireNonNull(result, "Forgot Password Start result must not be null");
    Objects.requireNonNull(
        transientResponse, "Forgot Password Start transient response must not be null");
    positive(ttl);
    validateCapturable(result.status());
    if (transientResponse.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(transientResponse.mediaType())) {
      throw new IllegalArgumentException("Forgot Password Start transient transport mismatch");
    }
    String body =
        envelope.encode(ForgotPasswordIdempotencyAction.START.action(), safePayload(result));
    if (result.status() != StartForgotPasswordResultStatus.STARTED) {
      return Mono.just(captured(transientResponse, body, null));
    }
    if (blank(result.authFlowSessionId())
        || blank(result.identityType())
        || blank(result.csrfToken())) {
      return Mono.error(
          new IllegalArgumentException("Forgot Password Start success is incomplete"));
    }
    return protectedReplay
        .store(
            new ForgotPasswordStartProtectedReplayState(
                result.authFlowSessionId(), result.identityType(), clock.instant().plus(ttl)),
            ttl)
        .map(reference -> captured(transientResponse, body, reference));
  }

  public Mono<StartForgotPasswordResult> hydrate(
      IdempotentTerminalResponseSnapshot snapshot, int sessionTtlSeconds) {
    Objects.requireNonNull(snapshot, "Forgot Password Start snapshot must not be null");
    if (sessionTtlSeconds <= 0) {
      throw new IllegalArgumentException("Forgot Password Start session TTL must be positive");
    }
    if (snapshot.schemaVersion() != SCHEMA_VERSION
        || !MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())) {
      return Mono.error(
          new IllegalArgumentException("Forgot Password Start replay transport is invalid"));
    }
    SafePayload payload =
        envelope.decodePayload(
            ForgotPasswordIdempotencyAction.START.action(),
            snapshot.serializedSafeBody(),
            SafePayload.class);
    StartForgotPasswordResultStatus status = status(payload.status());
    validateCapturable(status);
    if (snapshot.httpStatus() != httpStatus(status)) {
      return Mono.error(
          new IllegalArgumentException("Forgot Password Start replay HTTP status mismatch"));
    }
    if (status != StartForgotPasswordResultStatus.STARTED) {
      if (snapshot.protectedReplayReference() != null) {
        return Mono.error(
            new IllegalArgumentException(
                "Forgot Password Start rejection cannot carry protected replay"));
      }
      return Mono.just(rejection(status, payload));
    }
    if (snapshot.protectedReplayReference() == null) {
      return Mono.error(
          new IllegalArgumentException("Forgot Password Start protected replay is required"));
    }
    return protectedReplay
        .load(snapshot.protectedReplayReference())
        .switchIfEmpty(
            Mono.error(
                new IllegalArgumentException(
                    "Forgot Password Start protected replay is unavailable")))
        .flatMap(state -> hydrateStarted(state, sessionTtlSeconds));
  }

  private Mono<StartForgotPasswordResult> hydrateStarted(
      ForgotPasswordStartProtectedReplayState state, int sessionTtlSeconds) {
    if (!state.expiresAt().isAfter(clock.instant())) {
      return Mono.error(
          new IllegalArgumentException("Forgot Password Start protected replay is expired"));
    }
    long remaining = Duration.between(clock.instant(), state.expiresAt()).toSeconds();
    int ttl = Math.toIntExact(Math.min(remaining, sessionTtlSeconds));
    if (ttl <= 0) {
      return Mono.error(
          new IllegalArgumentException("Forgot Password Start protected replay is expired"));
    }
    return csrf.createForSession(
            new CreateAuthFlowSessionCsrfCommand(
                new AuthFlowSessionId(state.authFlowSessionId()), clock.instant(), ttl, null))
        .map(
            token ->
                new StartForgotPasswordResult(
                    StartForgotPasswordResultStatus.COMPLETED_REPLAY,
                    WorkflowNextStep.ENTER_ID,
                    token.rawToken(),
                    state.identityType(),
                    state.authFlowSessionId(),
                    List.of(),
                    null));
  }

  private IdempotentCapturedResponse captured(
      HydratedIdempotentResponse transientResponse,
      String safeBody,
      com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference reference) {
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            SCHEMA_VERSION,
            transientResponse.httpStatus(),
            MEDIA_TYPE,
            safeBody,
            transientResponse.safeHeaders(),
            reference);
    var hydrated =
        reference == null
            ? transientResponse
            : transientResponse.withProtectedReplayReference(reference);
    return new IdempotentCapturedResponse(hydrated, snapshot);
  }

  private SafePayload safePayload(StartForgotPasswordResult result) {
    return new SafePayload(
        result.status().name(),
        result.nextStep().name(),
        result.identityType(),
        result.errors().stream()
            .map(error -> new ErrorWire(error.code(), error.target()))
            .toList());
  }

  private StartForgotPasswordResult rejection(
      StartForgotPasswordResultStatus status, SafePayload payload) {
    WorkflowNextStep nextStep;
    try {
      nextStep = WorkflowNextStep.valueOf(payload.nextStep());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Forgot Password Start replay next step is unsupported");
    }
    List<StartForgotPasswordError> errors =
        payload.errors() == null
            ? List.of()
            : payload.errors().stream()
                .map(error -> new StartForgotPasswordError(error.code(), error.target(), null))
                .toList();
    return new StartForgotPasswordResult(
        status, nextStep, null, payload.identityType(), null, errors, null);
  }

  private StartForgotPasswordResultStatus status(String value) {
    try {
      return StartForgotPasswordResultStatus.valueOf(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Forgot Password Start replay status is unsupported");
    }
  }

  private void validateCapturable(StartForgotPasswordResultStatus status) {
    if (status != StartForgotPasswordResultStatus.STARTED
        && status != StartForgotPasswordResultStatus.USERNAME_NOT_FOUND
        && status != StartForgotPasswordResultStatus.AUTH_FLOW_INVALID) {
      throw new IllegalArgumentException("Forgot Password Start result is not capturable");
    }
  }

  public static int httpStatus(StartForgotPasswordResultStatus status) {
    return switch (status) {
      case STARTED, COMPLETED_REPLAY -> 200;
      case USERNAME_NOT_FOUND -> 422;
      case AUTH_FLOW_INVALID -> 400;
      default ->
          throw new IllegalArgumentException("Forgot Password Start result is not capturable");
    };
  }

  private static void positive(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("Forgot Password Start replay TTL must be positive");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public record SafePayload(
      String status, String nextStep, String identityType, List<ErrorWire> errors) {}

  public record ErrorWire(String code, String target) {}
}
