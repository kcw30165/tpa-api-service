package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordError;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Converts typed START results to transient responses and safe persisted snapshots. */
public final class ForgotPasswordStartExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final TransientJsonPort json;
  private final ForgotPasswordStartReplayCodec replayCodec;
  private final int sessionTtlSeconds;

  public ForgotPasswordStartExecutionBridge(
      TransientJsonPort json,
      ForgotPasswordStartReplayCodec replayCodec,
      int sessionTtlSeconds) {
    this.json = Objects.requireNonNull(json);
    this.replayCodec = Objects.requireNonNull(replayCodec);
    if (sessionTtlSeconds <= 0) throw new IllegalArgumentException("session TTL must be positive");
    this.sessionTtlSeconds = sessionTtlSeconds;
  }

  public HydratedIdempotentResponse toTransientResponse(StartForgotPasswordResult result) {
    Objects.requireNonNull(result, "Forgot Password Start result must not be null");
    try {
      return new HydratedIdempotentResponse(
          ForgotPasswordStartReplayCodec.httpStatus(result.status()),
          MEDIA_TYPE,
          json.write(toWire(result)),
          Map.of());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Forgot Password Start transient response encoding failed");
    }
  }

  public StartForgotPasswordResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response, "Forgot Password Start transient response must not be null");
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException("Forgot Password Start transient media type is invalid");
    }
    try {
      TransientWire wire = json.read(response.serializedBody(), TransientWire.class);
      StartForgotPasswordResultStatus status = StartForgotPasswordResultStatus.valueOf(wire.status());
      WorkflowNextStep nextStep = WorkflowNextStep.valueOf(wire.nextStep());
      List<StartForgotPasswordError> errors =
          wire.errors() == null
              ? List.of()
              : wire.errors().stream()
                  .map(error -> new StartForgotPasswordError(error.code(), error.target(), error.message()))
                  .toList();
      return new StartForgotPasswordResult(
          status,
          nextStep,
          wire.csrfToken(),
          wire.identityType(),
          wire.authFlowSessionId(),
          errors,
          null);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Forgot Password Start transient response decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(
      HydratedIdempotentResponse response, Duration ttl) {
    return replayCodec.capture(fromTransientResponse(response), response, ttl);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return replayCodec.hydrate(snapshot, sessionTtlSeconds).map(this::toTransientResponse);
  }

  private TransientWire toWire(StartForgotPasswordResult result) {
    return new TransientWire(
        result.status().name(),
        result.nextStep().name(),
        result.csrfToken(),
        result.identityType(),
        result.authFlowSessionId(),
        result.errors().stream()
            .map(error -> new ErrorWire(error.code(), error.target(), error.message()))
            .toList());
  }

  private record TransientWire(
      String status,
      String nextStep,
      String csrfToken,
      String identityType,
      String authFlowSessionId,
      List<ErrorWire> errors) {}

  private record ErrorWire(String code, String target, String message) {}
}
