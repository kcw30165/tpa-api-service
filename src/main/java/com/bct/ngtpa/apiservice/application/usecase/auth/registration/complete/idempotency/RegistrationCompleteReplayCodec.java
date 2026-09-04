package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedResponseBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedResponseEnvelope;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedTerminalResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict safe terminal codec for Registration Complete. */
public final class RegistrationCompleteReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationSharedResponseEnvelope envelope =
      new RegistrationSharedResponseEnvelope();
  private final RegistrationSharedResponseBridge bridge = new RegistrationSharedResponseBridge();

  public IdempotentCapturedResponse capture(CompleteRegistrationResult result) {
    Objects.requireNonNull(result, "Registration Complete result must not be null");
    String body = envelope.encode(RegistrationSharedIdempotencyAction.COMPLETE, payload(result));
    return bridge.capture(
        new RegistrationSharedTerminalResponse(
            RegistrationSharedIdempotencyAction.COMPLETE,
            httpStatus(result.status()),
            MEDIA_TYPE,
            body,
            Map.of(),
            null));
  }

  public CompleteRegistrationResult hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    bridge.hydrate(RegistrationSharedIdempotencyAction.COMPLETE, snapshot);
    try {
      EnvelopeWire wire = mapper.readValue(snapshot.serializedSafeBody(), EnvelopeWire.class);
      if (!RegistrationSharedIdempotencyAction.COMPLETE.value().equals(wire.idempotencyAction())
          || wire.payload() == null) {
        throw new IllegalArgumentException("Registration Complete replay envelope is invalid");
      }
      return result(wire.payload());
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new IllegalArgumentException("Registration Complete replay decoding failed");
    }
  }

  private Payload payload(CompleteRegistrationResult result) {
    rejectLifecycleOnlyStatus(result.status());
    return new Payload(
        result.status().name(),
        result.nextStep().name(),
        result.registered(),
        result.clearAuthFlowCookie(),
        result.remainingTrusts().stream()
            .map(trust -> new TrustWire(trust.accountEnv(), trust.trustCode()))
            .toList(),
        result.errors().stream()
            .map(
                error ->
                    new ErrorWire(
                        error.type().name(),
                        error.code(),
                        error.message(),
                        error.targets(),
                        error.severity().name(),
                        error.source()))
            .toList());
  }

  private CompleteRegistrationResult result(Payload payload) {
    if (payload.status() == null
        || payload.nextStep() == null
        || payload.remainingTrusts() == null
        || payload.errors() == null) {
      throw new IllegalArgumentException("Registration Complete replay payload is invalid");
    }
    CompleteRegistrationResultStatus status =
        CompleteRegistrationResultStatus.valueOf(payload.status());
    rejectLifecycleOnlyStatus(status);
    List<RegistrationLookupAccount> trusts =
        payload.remainingTrusts().stream().map(this::trust).toList();
    List<WorkflowErrorDescriptor> errors =
        payload.errors().stream()
            .map(
                error ->
                    new WorkflowErrorDescriptor(
                        WorkflowErrorType.valueOf(error.type()),
                        error.code(),
                        error.message(),
                        error.targets(),
                        WorkflowErrorSeverity.valueOf(error.severity()),
                        error.source()))
            .toList();
    return new CompleteRegistrationResult(
        status,
        WorkflowNextStep.valueOf(payload.nextStep()),
        payload.registered(),
        payload.clearAuthFlowCookie(),
        trusts,
        errors);
  }

  private RegistrationLookupAccount trust(TrustWire trust) {
    if (trust == null
        || trust.accountEnv() == null
        || trust.accountEnv().isBlank()
        || trust.trustCode() == null
        || trust.trustCode().isBlank()) {
      throw new IllegalArgumentException("Registration Complete replay trust is invalid");
    }
    return new RegistrationLookupAccount(
        "", "", trust.accountEnv().trim(), trust.trustCode().trim());
  }

  private void rejectLifecycleOnlyStatus(CompleteRegistrationResultStatus status) {
    if (status == CompleteRegistrationResultStatus.COMPLETED_REPLAY
        || status == CompleteRegistrationResultStatus.UNKNOWN_OUTCOME) {
      throw new IllegalArgumentException(
          "Registration Complete lifecycle status is not a capturable public result");
    }
  }

  private int httpStatus(CompleteRegistrationResultStatus status) {
    rejectLifecycleOnlyStatus(status);
    return switch (status) {
      case REGISTERED, REGISTERED_WITH_REMAINING_TRUSTS -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, INVALID_STATE -> 400;
      case IDEMPOTENCY_KEY_REUSED, DUPLICATE_IN_PROGRESS -> 409;
      case DOWNSTREAM_REJECTED -> 422;
      case DOWNSTREAM_FAILED, CONTINUATION_LOOKUP_FAILED, SYSTEM_FAILED -> 500;
      case COMPLETED_REPLAY, UNKNOWN_OUTCOME ->
          throw new IllegalArgumentException(
              "Registration Complete lifecycle status is not a capturable public result");
    };
  }

  private record EnvelopeWire(String idempotencyAction, Payload payload) {}

  private record Payload(
      String status,
      String nextStep,
      boolean registered,
      boolean clearAuthFlowCookie,
      List<TrustWire> remainingTrusts,
      List<ErrorWire> errors) {}

  private record TrustWire(String accountEnv, String trustCode) {}

  private record ErrorWire(
      String type,
      String code,
      String message,
      List<String> targets,
      String severity,
      String source) {}
}
