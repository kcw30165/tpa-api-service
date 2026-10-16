package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyResponseSnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationUnregisteredTrust;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient and persisted-response bridge for OTP Verify shared execution. */
public final class RegistrationOtpVerifySharedExecutionBridge {
  private static final String FIELD_REMAINING_VERIFY_ATTEMPTS = "remainingVerifyAttempts";

  private static final String FIELD_IDEMPOTENCY_OUTCOME = "idempotencyOutcome";

  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationOtpVerifyReplayCodec codec;

  public RegistrationOtpVerifySharedExecutionBridge(RegistrationOtpVerifyReplayCodec codec) {
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(VerifyRegistrationOtpResult result) {
    Objects.requireNonNull(result);
    try {
      ObjectNode wire = mapper.createObjectNode();
      wire.put("status", result.status().name());
      wire.put("nextStep", result.nextStep().name());
      wire.set("unregisteredTrusts", mapper.valueToTree(result.unregisteredTrusts()));
      if (result.remainingVerifyAttempts() == null) {
        wire.putNull(FIELD_REMAINING_VERIFY_ATTEMPTS);
      } else {
        wire.put(FIELD_REMAINING_VERIFY_ATTEMPTS, result.remainingVerifyAttempts());
      }
      wire.set("messages", mapper.valueToTree(result.messages()));
      wire.set("errors", mapper.valueToTree(result.errors()));
      if (result.idempotencyOutcome() == null) {
        wire.putNull(FIELD_IDEMPOTENCY_OUTCOME);
      } else {
        wire.put(FIELD_IDEMPOTENCY_OUTCOME, result.idempotencyOutcome().name());
      }
      wire.set("replaySnapshot", mapper.valueToTree(result.replaySnapshot()));
      return new HydratedIdempotentResponse(
          transientStatus(result), MEDIA_TYPE, mapper.writeValueAsString(wire), Map.of());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "Registration OTP Verify transient encoding failed", exception);
    }
  }

  public VerifyRegistrationOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response);
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException("Registration OTP Verify transient media type is invalid");
    }
    try {
      JsonNode wire = mapper.readTree(response.serializedBody());
      requireObject(wire);
      wire = unwrapTerminalEnvelope(wire);
      requireObject(wire);
      return new VerifyRegistrationOtpResult(
          VerifyRegistrationOtpResultStatus.valueOf(requiredText(wire, "status")),
          WorkflowNextStep.valueOf(requiredText(wire, "nextStep")),
          readList(wire, "unregisteredTrusts", RegistrationUnregisteredTrust[].class),
          nullableInteger(wire, FIELD_REMAINING_VERIFY_ATTEMPTS),
          readList(wire, "messages", WorkflowMessageDescriptor[].class),
          readList(wire, "errors", WorkflowErrorDescriptor[].class),
          nullableEnum(wire, FIELD_IDEMPOTENCY_OUTCOME, RegistrationIdempotencyStartOutcome.class),
          nullableValue(wire, "replaySnapshot", RegistrationIdempotencyResponseSnapshot.class));
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Registration OTP Verify transient decoding failed", exception);
    }
  }

  private JsonNode unwrapTerminalEnvelope(JsonNode wire) {
    JsonNode action = wire.get("idempotencyAction");
    JsonNode payload = wire.get("payload");
    if (action == null && payload == null) {
      return wire;
    }
    if (action == null
        || !action.isTextual()
        || !"otp-verify".equals(action.asText())
        || payload == null
        || !payload.isObject()) {
      throw new IllegalArgumentException("Registration OTP Verify terminal envelope is invalid");
    }
    return payload;
  }

  private void requireObject(JsonNode wire) {
    if (wire == null || !wire.isObject()) {
      throw new IllegalArgumentException("Registration OTP Verify transient body is invalid");
    }
  }

  private String requiredText(JsonNode wire, String field) {
    JsonNode value = wire.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException(
          "Registration OTP Verify transient field is invalid: " + field);
    }
    return value.asText();
  }

  private Integer nullableInteger(JsonNode wire, String field) {
    JsonNode value = wire.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isInt()) {
      throw new IllegalArgumentException(
          "Registration OTP Verify transient field is invalid: " + field);
    }
    return value.intValue();
  }

  private <E extends Enum<E>> E nullableEnum(JsonNode wire, String field, Class<E> type) {
    JsonNode value = wire.get(field);
    return value == null || value.isNull() ? null : Enum.valueOf(type, requiredText(wire, field));
  }

  private <T> T nullableValue(JsonNode wire, String field, Class<T> type)
      throws JsonProcessingException {
    JsonNode value = wire.get(field);
    return value == null || value.isNull() ? null : mapper.treeToValue(value, type);
  }

  private <T> List<T> readList(JsonNode wire, String field, Class<T[]> arrayType)
      throws JsonProcessingException {
    JsonNode value = wire.get(field);
    if (value == null || value.isNull()) {
      return List.of();
    }
    if (!value.isArray()) {
      throw new IllegalArgumentException(
          "Registration OTP Verify transient field is invalid: " + field);
    }
    return List.copyOf(Arrays.asList(mapper.treeToValue(value, arrayType)));
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return Mono.fromSupplier(() -> codec.capture(fromTransientResponse(response)));
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return Mono.fromSupplier(() -> toTransientResponse(codec.hydrate(snapshot)));
  }

  private int transientStatus(VerifyRegistrationOtpResult result) {
    return switch (result.status()) {
      case VERIFIED -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, OTP_INVALID -> 400;
      case DUPLICATE_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED -> 409;
      case OTP_EXPIRED, OTP_LOCKED, NO_ELIGIBLE_TRUSTS -> 422;
      case TRUST_LOOKUP_FAILED, SYSTEM_FAILED -> 500;
      case COMPLETED_REPLAY ->
          throw new IllegalArgumentException(
              "OTP Verify lifecycle replay status is not a transient public result");
    };
  }
}
