package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.complete;

import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class ForgotPasswordCompleteExecutionBridge {
  private final TransientJsonPort json;
  private final ForgotPasswordCompleteReplayCodec codec;

  public ForgotPasswordCompleteExecutionBridge(
      TransientJsonPort json, ForgotPasswordCompleteReplayCodec codec) {
    this.json = Objects.requireNonNull(json);
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(CompleteForgotPasswordResult result) {
    return new HydratedIdempotentResponse(
        ForgotPasswordCompleteReplayCodec.httpStatus(result.status()),
        "application/json",
        json.write(Wire.from(result)),
        Map.of());
  }

  public CompleteForgotPasswordResult fromTransientResponse(HydratedIdempotentResponse response) {
    if (!"application/json".equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null)
      throw new IllegalArgumentException("COMPLETE transient transport is invalid");
    var result = json.read(response.serializedBody(), Wire.class).toResult();
    if (response.httpStatus() != ForgotPasswordCompleteReplayCodec.httpStatus(result.status()))
      throw new IllegalArgumentException("COMPLETE transient HTTP status mismatch");
    return result;
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return codec.capture(fromTransientResponse(response), response);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return codec.hydrate(snapshot).map(this::toTransientResponse);
  }

  public record Wire(
      String status,
      String nextStep,
      boolean clearAuthFlowCookie,
      boolean clearXsrfCookie,
      List<ForgotPasswordCompleteReplayCodec.ErrorWire> errors) {
    static Wire from(CompleteForgotPasswordResult r) {
      return new Wire(
          r.status().name(),
          r.nextStep().name(),
          r.clearAuthFlowCookie(),
          r.clearXsrfCookie(),
          r.errors().stream().map(ForgotPasswordCompleteReplayCodec.ErrorWire::from).toList());
    }

    CompleteForgotPasswordResult toResult() {
      if (status == null || nextStep == null || errors == null)
        throw new IllegalArgumentException();
      return new CompleteForgotPasswordResult(
          CompleteForgotPasswordStatus.valueOf(status),
          WorkflowNextStep.valueOf(nextStep),
          clearAuthFlowCookie,
          clearXsrfCookie,
          errors.stream().map(ForgotPasswordCompleteReplayCodec.ErrorWire::toError).toList());
    }
  }
}
