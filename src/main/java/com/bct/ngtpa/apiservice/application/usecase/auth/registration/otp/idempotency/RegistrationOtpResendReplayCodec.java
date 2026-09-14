package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedResponseBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedResponseEnvelope;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedTerminalResponse;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict safe terminal codec for Registration OTP Resend. */
public final class RegistrationOtpResendReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationSharedResponseEnvelope envelope =
      new RegistrationSharedResponseEnvelope();
  private final RegistrationSharedResponseBridge bridge = new RegistrationSharedResponseBridge();

  public IdempotentCapturedResponse capture(ResendRegistrationOtpResult result) {
    Objects.requireNonNull(result, "OTP Resend result must not be null");
    String body = envelope.encode(RegistrationSharedIdempotencyAction.OTP_RESEND, payload(result));
    return bridge.capture(
        new RegistrationSharedTerminalResponse(
            RegistrationSharedIdempotencyAction.OTP_RESEND,
            httpStatus(result.status()),
            MEDIA_TYPE,
            body,
            Map.of(),
            null));
  }

  public ResendRegistrationOtpResult hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    bridge.hydrate(RegistrationSharedIdempotencyAction.OTP_RESEND, snapshot);
    try {
      EnvelopeWire wire = mapper.readValue(snapshot.serializedSafeBody(), EnvelopeWire.class);
      if (!RegistrationSharedIdempotencyAction.OTP_RESEND.value().equals(wire.idempotencyAction())
          || wire.payload() == null) {
        throw new IllegalArgumentException("Registration OTP Resend replay envelope is invalid");
      }
      return result(wire.payload());
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new IllegalArgumentException("Registration OTP Resend replay decoding failed");
    }
  }

  private Payload payload(ResendRegistrationOtpResult result) {
    return new Payload(
        result.status().name(),
        result.nextStep().name(),
        result.maskedDestination(),
        result.expiresInSeconds(),
        result.resendCooldownSeconds(),
        result.remainingResendAttempts(),
        result.retryAfterSeconds(),
        result.messages().stream()
            .map(
                message ->
                    new MessageWire(
                        message.type().name(), message.code(), message.message(), message.target()))
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

  private ResendRegistrationOtpResult result(Payload payload) {
    if (payload.status() == null || payload.nextStep() == null) {
      throw new IllegalArgumentException("Registration OTP Resend replay payload is invalid");
    }
    List<WorkflowMessageDescriptor> messages =
        payload.messages() == null
            ? List.of()
            : payload.messages().stream()
                .map(
                    message ->
                        new WorkflowMessageDescriptor(
                            WorkflowMessageType.valueOf(message.type()),
                            message.code(),
                            message.message(),
                            message.target()))
                .toList();
    List<WorkflowErrorDescriptor> errors =
        payload.errors() == null
            ? List.of()
            : payload.errors().stream()
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
    return new ResendRegistrationOtpResult(
        ResendRegistrationOtpResultStatus.valueOf(payload.status()),
        WorkflowNextStep.valueOf(payload.nextStep()),
        payload.maskedDestination(),
        payload.expiresInSeconds(),
        payload.resendCooldownSeconds(),
        payload.remainingResendAttempts(),
        payload.retryAfterSeconds(),
        messages,
        errors,
        RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY,
        null);
  }

  private int httpStatus(ResendRegistrationOtpResultStatus status) {
    return switch (status) {
      case RESENT -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID -> 400;
      case DUPLICATE_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED -> 409;
      case COOLDOWN_ACTIVE, SEND_RATE_LIMITED, MAX_RESEND_ATTEMPTS_REACHED -> 429;
      case MESSAGE_DELIVERY_FAILED, SYSTEM_FAILED -> 500;
      case COMPLETED_REPLAY ->
          throw new IllegalArgumentException(
              "OTP Resend lifecycle replay status cannot be captured as a public result");
    };
  }

  private record EnvelopeWire(String idempotencyAction, Payload payload) {}

  private record Payload(
      String status,
      String nextStep,
      String maskedDestination,
      int expiresInSeconds,
      int resendCooldownSeconds,
      int remainingResendAttempts,
      Integer retryAfterSeconds,
      List<MessageWire> messages,
      List<ErrorWire> errors) {}

  private record MessageWire(String type, String code, String message, String target) {}

  private record ErrorWire(
      String type,
      String code,
      String message,
      List<String> targets,
      String severity,
      String source) {}
}
