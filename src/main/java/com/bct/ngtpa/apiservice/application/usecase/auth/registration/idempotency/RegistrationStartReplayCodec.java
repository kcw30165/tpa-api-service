package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
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

/** Typed safe Start result codec plus separately protected replay hydration. */
public final class RegistrationStartReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationSharedResponseEnvelope envelope =
      new RegistrationSharedResponseEnvelope();
  private final RegistrationSharedResponseBridge bridge = new RegistrationSharedResponseBridge();

  public Mono<IdempotentCapturedResponse> capture(
      StartRegistrationResult result,
      RegistrationStartProtectedReplayPort protectedReplay,
      Duration ttl) {
    Objects.requireNonNull(result, "registration Start result must not be null");
    Objects.requireNonNull(protectedReplay, "protected replay port must not be null");
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("registration Start replay TTL must be positive");
    }
    StartSafePayload payload = toPayload(result);
    String safeBody = envelope.encode(RegistrationSharedIdempotencyAction.START, payload);
    if (!requiresProtectedReplay(result)) {
      return Mono.just(bridge.capture(terminal(result, safeBody, null)));
    }
    return protectedReplay
        .store(
            new RegistrationStartProtectedReplayState(
                result.csrfToken(), result.authFlowSessionId()),
            ttl)
        .map(reference -> bridge.capture(terminal(result, safeBody, reference.value())));
  }

  public Mono<StartRegistrationResult> hydrate(
      IdempotentTerminalResponseSnapshot snapshot,
      RegistrationStartProtectedReplayPort protectedReplay) {
    Objects.requireNonNull(protectedReplay, "protected replay port must not be null");
    bridge.hydrate(RegistrationSharedIdempotencyAction.START, snapshot);
    StartSafePayload payload = decodePayload(snapshot.serializedSafeBody());
    if (snapshot.protectedReplayReference() == null) {
      return Mono.just(toResult(payload, null));
    }
    return protectedReplay
        .load(snapshot.protectedReplayReference())
        .switchIfEmpty(
            Mono.error(
                new IllegalArgumentException("Registration Start protected replay is unavailable")))
        .map(state -> toResult(payload, state));
  }

  private RegistrationSharedTerminalResponse terminal(
      StartRegistrationResult result, String body, String protectedReference) {
    return new RegistrationSharedTerminalResponse(
        RegistrationSharedIdempotencyAction.START,
        httpStatus(result.status()),
        MEDIA_TYPE,
        body,
        Map.of(),
        protectedReference);
  }

  private StartSafePayload toPayload(StartRegistrationResult result) {
    return new StartSafePayload(
        result.status().name(),
        result.nextStep().name(),
        result.maskedDestination(),
        result.otpExpiresInSeconds(),
        result.resendCooldownSeconds(),
        result.remainingResendAttempts(),
        result.errors().stream()
            .map(error -> new ErrorWire(error.errorKey().value(), error.target(), error.message()))
            .toList());
  }

  private StartSafePayload decodePayload(String envelopeJson) {
    try {
      EnvelopeWire wire = mapper.readValue(envelopeJson, EnvelopeWire.class);
      if (!RegistrationSharedIdempotencyAction.START.value().equals(wire.idempotencyAction())
          || wire.payload() == null) {
        throw new IllegalArgumentException("Registration Start replay envelope is invalid");
      }
      return wire.payload();
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration Start replay decoding failed");
    }
  }

  private StartRegistrationResult toResult(
      StartSafePayload payload, RegistrationStartProtectedReplayState protectedState) {
    StartRegistrationResultStatus status = StartRegistrationResultStatus.valueOf(payload.status());
    WorkflowNextStep nextStep = WorkflowNextStep.valueOf(payload.nextStep());
    List<StartRegistrationError> errors =
        payload.errors() == null
            ? List.of()
            : payload.errors().stream()
                .map(
                    error ->
                        new StartRegistrationError(
                            errorKey(error.code()), error.target(), error.message()))
                .toList();
    return new StartRegistrationResult(
        status,
        nextStep,
        payload.maskedDestination(),
        payload.otpExpiresInSeconds(),
        payload.resendCooldownSeconds(),
        payload.remainingResendAttempts(),
        protectedState == null ? null : protectedState.csrfToken(),
        protectedState == null ? null : protectedState.authFlowSessionId(),
        List.of(),
        errors,
        RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY,
        null);
  }

  private WorkflowErrorKey errorKey(String code) {
    for (RegistrationErrorKey key : RegistrationErrorKey.values()) {
      if (key.value().equals(code)) {
        return key;
      }
    }
    for (SharedWorkflowErrorKey key : SharedWorkflowErrorKey.values()) {
      if (key.value().equals(code)) {
        return key;
      }
    }
    throw new IllegalArgumentException("Registration Start replay error code is unsupported");
  }

  private boolean requiresProtectedReplay(StartRegistrationResult result) {
    return result.status() == StartRegistrationResultStatus.STARTED;
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
              "Registration Start lifecycle replay status cannot be captured as a public result");
    };
  }

  private record EnvelopeWire(String idempotencyAction, StartSafePayload payload) {}

  private record StartSafePayload(
      String status,
      String nextStep,
      String maskedDestination,
      int otpExpiresInSeconds,
      int resendCooldownSeconds,
      int remainingResendAttempts,
      List<ErrorWire> errors) {}

  private record ErrorWire(String code, String target, String message) {}
}
