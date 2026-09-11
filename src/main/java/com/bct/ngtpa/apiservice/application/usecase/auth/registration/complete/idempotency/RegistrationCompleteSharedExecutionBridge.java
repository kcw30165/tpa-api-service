package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed in-memory bridge between Registration Complete and the context-neutral executor. */
public final class RegistrationCompleteSharedExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final RegistrationCompleteReplayCodec codec;

  public RegistrationCompleteSharedExecutionBridge(RegistrationCompleteReplayCodec codec) {
    this.codec = Objects.requireNonNull(codec, "Complete replay codec must not be null");
  }

  public HydratedIdempotentResponse toTransientResponse(CompleteRegistrationResult result) {
    Objects.requireNonNull(result, "Complete result must not be null");
    try {
      return new HydratedIdempotentResponse(
          httpStatus(result.status()),
          MEDIA_TYPE,
          mapper.writeValueAsString(toWire(result)),
          Map.of());
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(
          "Registration Complete transient response encoding failed");
    }
  }

  public CompleteRegistrationResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response, "Complete hydrated response must not be null");
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException("Registration Complete transient media type is invalid");
    }
    try {
      return fromWire(mapper.readValue(response.serializedBody(), TransientWire.class));
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Registration Complete transient response decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return Mono.fromSupplier(() -> codec.capture(fromTransientResponse(response)));
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return Mono.fromSupplier(() -> toTransientResponse(codec.hydrate(snapshot)));
  }

  private TransientWire toWire(CompleteRegistrationResult result) {
    return new TransientWire(
        result.status().name(),
        result.nextStep().name(),
        result.registered(),
        result.clearAuthFlowCookie(),
        result.remainingTrusts().stream()
            .map(
                trust ->
                    new TrustWire(
                        trust.accountEnv(),
                        trust.trustCode(),
                        trust.schemeType(),
                        trust.policyNo(),
                        trust.certNo(),
                        trust.termStatus(),
                        trust.termCompleteDate(),
                        trust.registered(),
                        trust.customerId()))
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

  private CompleteRegistrationResult fromWire(TransientWire wire) {
    if (wire == null
        || wire.status() == null
        || wire.nextStep() == null
        || wire.remainingTrusts() == null
        || wire.errors() == null) {
      throw new IllegalArgumentException("Registration Complete transient response is invalid");
    }
    return new CompleteRegistrationResult(
        CompleteRegistrationResultStatus.valueOf(wire.status()),
        WorkflowNextStep.valueOf(wire.nextStep()),
        wire.registered(),
        wire.clearAuthFlowCookie(),
        wire.remainingTrusts().stream()
            .map(
                trust ->
                    new RegistrationLookupAccount(
                        trust.accountEnv(),
                        trust.trustCode(),
                        trust.schemeType(),
                        trust.policyNo(),
                        trust.certNo(),
                        trust.termStatus(),
                        trust.termCompleteDate(),
                        trust.registered(),
                        trust.customerId()))
            .toList(),
        wire.errors().stream()
            .map(
                error ->
                    new WorkflowErrorDescriptor(
                        WorkflowErrorType.valueOf(error.type()),
                        error.code(),
                        error.message(),
                        error.targets(),
                        WorkflowErrorSeverity.valueOf(error.severity()),
                        error.source()))
            .toList());
  }

  private int httpStatus(CompleteRegistrationResultStatus status) {
    return switch (status) {
      case REGISTERED, REGISTERED_WITH_REMAINING_TRUSTS -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID, INVALID_STATE -> 400;
      case IDEMPOTENCY_KEY_REUSED, DUPLICATE_IN_PROGRESS -> 409;
      case DOWNSTREAM_REJECTED -> 422;
      case DOWNSTREAM_FAILED, CONTINUATION_LOOKUP_FAILED, SYSTEM_FAILED, UNKNOWN_OUTCOME -> 500;
      case COMPLETED_REPLAY ->
          throw new IllegalArgumentException(
              "Complete replay lifecycle status is not a transient public result");
    };
  }

  private record TransientWire(
      String status,
      String nextStep,
      boolean registered,
      boolean clearAuthFlowCookie,
      List<TrustWire> remainingTrusts,
      List<ErrorWire> errors) {}

  private record TrustWire(
      String accountEnv,
      String trustCode,
      String schemeType,
      String policyNo,
      String certNo,
      String termStatus,
      String termCompleteDate,
      Boolean registered,
      String customerId) {}

  private record ErrorWire(
      String type,
      String code,
      String message,
      List<String> targets,
      String severity,
      String source) {}
}
