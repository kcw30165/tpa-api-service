package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Bridges typed Start results through the context-neutral executor. The transient hydrated body may
 * carry protected values in memory; only RegistrationStartReplayCodec creates the persisted safe
 * snapshot, where protected values are replaced by an opaque reference.
 */
public final class RegistrationStartSharedExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationStartReplayCodec replayCodec;
  private final RegistrationStartProtectedReplayPort protectedReplay;

  public RegistrationStartSharedExecutionBridge(
      RegistrationStartReplayCodec replayCodec,
      RegistrationStartProtectedReplayPort protectedReplay) {
    this.replayCodec = Objects.requireNonNull(replayCodec, "Start replay codec must not be null");
    this.protectedReplay =
        Objects.requireNonNull(protectedReplay, "Start protected replay port must not be null");
  }

  public HydratedIdempotentResponse toTransientResponse(StartRegistrationResult result) {
    Objects.requireNonNull(result, "Start result must not be null");
    try {
      return new HydratedIdempotentResponse(
          httpStatus(result.status()),
          MEDIA_TYPE,
          mapper.writeValueAsString(toWire(result)),
          Map.of());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration Start transient response encoding failed");
    }
  }

  public StartRegistrationResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response, "Start hydrated response must not be null");
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException(
          "Registration Start transient response media type is invalid");
    }
    try {
      return fromWire(mapper.readValue(response.serializedBody(), TransientWire.class));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration Start transient response decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(
      HydratedIdempotentResponse response, Duration ttl) {
    return replayCodec.capture(fromTransientResponse(response), protectedReplay, ttl);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return replayCodec.hydrate(snapshot, protectedReplay).map(this::toTransientResponse);
  }

  private TransientWire toWire(StartRegistrationResult result) {
    return new TransientWire(
        result.status().name(),
        result.nextStep().name(),
        result.maskedDestination(),
        result.otpExpiresInSeconds(),
        result.resendCooldownSeconds(),
        result.remainingResendAttempts(),
        result.csrfToken(),
        result.authFlowSessionId(),
        result.errors().stream()
            .map(error -> new ErrorWire(error.errorKey().value(), error.target(), error.message()))
            .toList());
  }

  private StartRegistrationResult fromWire(TransientWire wire) {
    if (wire == null || wire.status() == null || wire.nextStep() == null) {
      throw new IllegalArgumentException("Registration Start transient response is invalid");
    }
    List<StartRegistrationError> errors =
        wire.errors() == null
            ? List.of()
            : wire.errors().stream()
                .map(
                    error ->
                        new StartRegistrationError(
                            errorKey(error.code()), error.target(), error.message()))
                .toList();
    return new StartRegistrationResult(
        StartRegistrationResultStatus.valueOf(wire.status()),
        WorkflowNextStep.valueOf(wire.nextStep()),
        wire.maskedDestination(),
        wire.otpExpiresInSeconds(),
        wire.resendCooldownSeconds(),
        wire.remainingResendAttempts(),
        wire.csrfToken(),
        wire.authFlowSessionId(),
        List.of(),
        errors,
        RegistrationIdempotencyStartOutcome.STARTED,
        null);
  }

  private WorkflowErrorKey errorKey(String code) {
    for (RegistrationErrorKey key : RegistrationErrorKey.values()) {
      if (key.value().equals(code)) return key;
    }
    for (SharedWorkflowErrorKey key : SharedWorkflowErrorKey.values()) {
      if (key.value().equals(code)) return key;
    }
    throw new IllegalArgumentException("Registration Start transient error code is unsupported");
  }

  private int httpStatus(StartRegistrationResultStatus status) {
    return switch (status) {
      case STARTED -> 200;
      case VALIDATION_FAILED -> 400;
      case VERIFICATION_FAILED, DOWNSTREAM_REJECTED -> 422;
      case DUPLICATE_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED -> 409;
      case MESSAGE_DELIVERY_FAILED, SYSTEM_FAILED -> 500;
      case COMPLETED_REPLAY ->
          throw new IllegalArgumentException(
              "Registration Start replay lifecycle status is not a transient public result");
    };
  }

  private record TransientWire(
      String status,
      String nextStep,
      String maskedDestination,
      int otpExpiresInSeconds,
      int resendCooldownSeconds,
      int remainingResendAttempts,
      String csrfToken,
      String authFlowSessionId,
      List<ErrorWire> errors) {}

  private record ErrorWire(String code, String target, String message) {}
}
