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
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationUnregisteredTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict safe terminal codec for Registration OTP Verify. */
public final class RegistrationOtpVerifyReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationSharedResponseEnvelope envelope =
      new RegistrationSharedResponseEnvelope();
  private final RegistrationSharedResponseBridge bridge = new RegistrationSharedResponseBridge();

  public IdempotentCapturedResponse capture(VerifyRegistrationOtpResult result) {
    Objects.requireNonNull(result, "OTP Verify result must not be null");
    String body = envelope.encode(RegistrationSharedIdempotencyAction.OTP_VERIFY, payload(result));
    return bridge.capture(
        new RegistrationSharedTerminalResponse(
            RegistrationSharedIdempotencyAction.OTP_VERIFY,
            httpStatus(result.status()),
            MEDIA_TYPE,
            body,
            Map.of(),
            null));
  }

  public VerifyRegistrationOtpResult hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    bridge.hydrate(RegistrationSharedIdempotencyAction.OTP_VERIFY, snapshot);
    try {
      EnvelopeWire wire = mapper.readValue(snapshot.serializedSafeBody(), EnvelopeWire.class);
      if (!RegistrationSharedIdempotencyAction.OTP_VERIFY.value().equals(wire.idempotencyAction())
          || wire.payload() == null) {
        throw new IllegalArgumentException("Registration OTP Verify replay envelope is invalid");
      }
      return result(wire.payload());
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new IllegalArgumentException("Registration OTP Verify replay decoding failed");
    }
  }

  private Payload payload(VerifyRegistrationOtpResult result) {
    return new Payload(
        result.status().name(),
        result.nextStep().name(),
        result.remainingVerifyAttempts(),
        result.unregisteredTrusts().stream()
            .map(trust -> new TrustWire(trust.accountEnv(), trust.trustCode()))
            .toList(),
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

  private VerifyRegistrationOtpResult result(Payload payload) {
    if (payload.status() == null
        || payload.nextStep() == null
        || payload.unregisteredTrusts() == null
        || payload.messages() == null
        || payload.errors() == null) {
      throw new IllegalArgumentException("Registration OTP Verify replay payload is invalid");
    }
    List<RegistrationUnregisteredTrust> trusts =
        payload.unregisteredTrusts().stream().map(this::trust).toList();
    List<WorkflowMessageDescriptor> messages =
        payload.messages().stream()
            .map(
                message ->
                    new WorkflowMessageDescriptor(
                        WorkflowMessageType.valueOf(message.type()), message.code(),
                        message.message(), message.target()))
            .toList();
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
    VerifyRegistrationOtpResultStatus status =
        VerifyRegistrationOtpResultStatus.valueOf(payload.status());
    if (status == VerifyRegistrationOtpResultStatus.COMPLETED_REPLAY) {
      throw new IllegalArgumentException(
          "OTP Verify lifecycle replay status is not a public result");
    }
    return new VerifyRegistrationOtpResult(
        status,
        WorkflowNextStep.valueOf(payload.nextStep()),
        trusts,
        payload.remainingVerifyAttempts(),
        messages,
        errors,
        RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY,
        null);
  }

  private RegistrationUnregisteredTrust trust(TrustWire trust) {
    if (trust == null
        || trust.accountEnv() == null
        || trust.accountEnv().isBlank()
        || trust.trustCode() == null
        || trust.trustCode().isBlank()) {
      throw new IllegalArgumentException("Registration OTP Verify replay trust is invalid");
    }
    return new RegistrationUnregisteredTrust(trust.accountEnv().trim(), trust.trustCode().trim());
  }

  private int httpStatus(VerifyRegistrationOtpResultStatus status) {
    return switch (status) {
      case VERIFIED -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, OTP_INVALID -> 400;
      case DUPLICATE_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED -> 409;
      case OTP_EXPIRED, OTP_LOCKED, NO_ELIGIBLE_TRUSTS -> 422;
      case TRUST_LOOKUP_FAILED, SYSTEM_FAILED -> 500;
      case COMPLETED_REPLAY ->
          throw new IllegalArgumentException(
              "OTP Verify lifecycle replay status cannot be captured as a public result");
    };
  }

  private record EnvelopeWire(String idempotencyAction, Payload payload) {}

  private record Payload(
      String status,
      String nextStep,
      Integer remainingVerifyAttempts,
      List<TrustWire> unregisteredTrusts,
      List<MessageWire> messages,
      List<ErrorWire> errors) {}

  private record TrustWire(String accountEnv, String trustCode) {}

  private record MessageWire(String type, String code, String message, String target) {}

  private record ErrorWire(
      String type,
      String code,
      String message,
      List<String> targets,
      String severity,
      String source) {}
}
