package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.complete;

import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import java.util.*;
import reactor.core.publisher.Mono;

/**
 * Strict public-result replay codec. Decoding uses FAIL_ON_UNKNOWN_PROPERTIES via
 * TransientJsonPort.
 */
public final class ForgotPasswordCompleteReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ActionTaggedIdempotencyResponseEnvelope envelope;

  public ForgotPasswordCompleteReplayCodec(ActionTaggedIdempotencyResponseEnvelope envelope) {
    this.envelope = Objects.requireNonNull(envelope);
  }

  public Mono<IdempotentCapturedResponse> capture(
      CompleteForgotPasswordResult result, HydratedIdempotentResponse response) {
    Objects.requireNonNull(result);
    Objects.requireNonNull(response);
    validate(result);
    if (response.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null)
      return Mono.error(new IllegalArgumentException("COMPLETE transient transport mismatch"));
    String body =
        envelope.encode(
            ForgotPasswordIdempotencyAction.COMPLETE.action(), SafePayload.from(result));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, response.httpStatus(), MEDIA_TYPE, body, Map.of(), null);
    return Mono.just(new IdempotentCapturedResponse(response, snapshot));
  }

  public Mono<CompleteForgotPasswordResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (snapshot.schemaVersion() != 1
        || !MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())
        || !snapshot.safeHeaders().isEmpty()
        || snapshot.protectedReplayReference() != null)
      return Mono.error(new IllegalArgumentException("COMPLETE replay transport is invalid"));
    try {
      var payload =
          envelope.decodePayload(
              ForgotPasswordIdempotencyAction.COMPLETE.action(),
              snapshot.serializedSafeBody(),
              SafePayload.class);
      var result = payload.toResult();
      validate(result);
      if (snapshot.httpStatus() != httpStatus(result.status()))
        throw new IllegalArgumentException();
      return Mono.just(result);
    } catch (RuntimeException e) {
      return Mono.error(new IllegalArgumentException("COMPLETE replay payload is invalid"));
    }
  }

  static int httpStatus(CompleteForgotPasswordStatus status) {
    return switch (status) {
      case SUCCESS -> 200;
      case VALIDATION_FAILED, INVALID_AUTH_FLOW, CSRF_INVALID -> 400;
      case IDEMPOTENCY_KEY_REUSED, DUPLICATE_IN_PROGRESS -> 409;
      case BUSINESS_REJECTED -> 422;
      case SYSTEM_FAILED -> 500;
      case UNKNOWN_OUTCOME ->
          throw new IllegalArgumentException("COMPLETE lifecycle status is not capturable");
    };
  }

  private static void validate(CompleteForgotPasswordResult result) {
    httpStatus(result.status());
    Objects.requireNonNull(result.nextStep());
    Objects.requireNonNull(result.errors());
  }

  public record SafePayload(
      String status,
      String nextStep,
      boolean clearAuthFlowCookie,
      boolean clearXsrfCookie,
      List<ErrorWire> errors) {
    static SafePayload from(CompleteForgotPasswordResult r) {
      return new SafePayload(
          r.status().name(),
          r.nextStep().name(),
          r.clearAuthFlowCookie(),
          r.clearXsrfCookie(),
          r.errors().stream().map(ErrorWire::from).toList());
    }

    CompleteForgotPasswordResult toResult() {
      if (status == null || nextStep == null || errors == null)
        throw new IllegalArgumentException();
      return new CompleteForgotPasswordResult(
          CompleteForgotPasswordStatus.valueOf(status),
          WorkflowNextStep.valueOf(nextStep),
          clearAuthFlowCookie,
          clearXsrfCookie,
          errors.stream().map(ErrorWire::toError).toList());
    }
  }

  public record ErrorWire(
      String type,
      String code,
      String message,
      List<String> targets,
      String severity,
      String source) {
    static ErrorWire from(WorkflowErrorDescriptor e) {
      return new ErrorWire(
          e.type().name(), e.code(), e.message(), e.targets(), e.severity().name(), e.source());
    }

    WorkflowErrorDescriptor toError() {
      return new WorkflowErrorDescriptor(
          WorkflowErrorType.valueOf(type),
          code,
          message,
          targets,
          WorkflowErrorSeverity.valueOf(severity),
          source);
    }
  }
}
