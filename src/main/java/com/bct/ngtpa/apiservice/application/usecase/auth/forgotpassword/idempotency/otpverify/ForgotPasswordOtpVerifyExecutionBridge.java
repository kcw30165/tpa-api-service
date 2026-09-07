package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient response boundary for OTP_VERIFY shared execution. */
public final class ForgotPasswordOtpVerifyExecutionBridge {
  private final TransientJsonPort json;
  private final ForgotPasswordOtpVerifyReplayCodec codec;

  public ForgotPasswordOtpVerifyExecutionBridge(
      TransientJsonPort json, ForgotPasswordOtpVerifyReplayCodec codec) {
    this.json = Objects.requireNonNull(json);
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(VerifyForgotPasswordOtpResult result) {
    return new HydratedIdempotentResponse(
        ForgotPasswordOtpVerifyReplayCodec.httpStatus(result.status()),
        "application/json",
        json.write(Wire.from(result)),
        Map.of());
  }

  public VerifyForgotPasswordOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    if (!"application/json".equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null) {
      throw new IllegalArgumentException("OTP_VERIFY transient transport is invalid");
    }
    VerifyForgotPasswordOtpResult result =
        json.read(response.serializedBody(), Wire.class).toResult();
    if (response.httpStatus() != ForgotPasswordOtpVerifyReplayCodec.httpStatus(result.status())) {
      throw new IllegalArgumentException("OTP_VERIFY transient HTTP status mismatch");
    }
    return result;
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return codec.capture(fromTransientResponse(response), response);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return codec.hydrate(snapshot).map(this::toTransientResponse);
  }

  private record Wire(String status, String nextStep, Integer remainingVerifyAttempts) {
    static Wire from(VerifyForgotPasswordOtpResult result) {
      return new Wire(
          result.status().name(), result.nextStep().name(), result.remainingVerifyAttempts());
    }

    VerifyForgotPasswordOtpResult toResult() {
      return new VerifyForgotPasswordOtpResult(
          VerifyForgotPasswordOtpStatus.valueOf(status),
          WorkflowNextStep.valueOf(nextStep),
          remainingVerifyAttempts);
    }
  }
}
