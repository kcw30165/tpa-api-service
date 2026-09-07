package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient response boundary for OTP_RESEND shared execution. */
public final class ForgotPasswordOtpResendExecutionBridge {
  private final TransientJsonPort json;
  private final ForgotPasswordOtpResendReplayCodec codec;

  public ForgotPasswordOtpResendExecutionBridge(
      TransientJsonPort json, ForgotPasswordOtpResendReplayCodec codec) {
    this.json = Objects.requireNonNull(json);
    this.codec = Objects.requireNonNull(codec);
  }

  public HydratedIdempotentResponse toTransientResponse(ResendForgotPasswordOtpResult result) {
    return new HydratedIdempotentResponse(
        ForgotPasswordOtpResendReplayCodec.httpStatus(result.status()),
        "application/json",
        json.write(Wire.from(result)),
        Map.of());
  }

  public ResendForgotPasswordOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    if (!"application/json".equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()) {
      throw new IllegalArgumentException("OTP_RESEND transient transport is invalid");
    }
    Wire wire = json.read(response.serializedBody(), Wire.class);
    ResendForgotPasswordOtpResult result = wire.toResult();
    if (response.httpStatus() != ForgotPasswordOtpResendReplayCodec.httpStatus(result.status())) {
      throw new IllegalArgumentException("OTP_RESEND transient HTTP status mismatch");
    }
    return result;
  }

  public Mono<IdempotentCapturedResponse> capture(
      HydratedIdempotentResponse response, ProtectedReplayReference reference) {
    return codec.capture(fromTransientResponse(response), response, reference);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return codec.hydrate(snapshot).map(this::toTransientResponse);
  }

  private record Wire(
      String status,
      String maskedDestination,
      Integer expiresInSeconds,
      Integer resendCooldownSeconds,
      Integer remainingResendAttempts,
      Integer retryAfterSeconds) {
    static Wire from(ResendForgotPasswordOtpResult result) {
      return new Wire(
          result.status().name(),
          result.maskedDestination(),
          result.expiresInSeconds(),
          result.resendCooldownSeconds(),
          result.remainingResendAttempts(),
          result.retryAfterSeconds());
    }

    ResendForgotPasswordOtpResult toResult() {
      return new ResendForgotPasswordOtpResult(
          ResendForgotPasswordOtpStatus.valueOf(status),
          maskedDestination,
          expiresInSeconds,
          resendCooldownSeconds,
          remainingResendAttempts,
          retryAfterSeconds);
    }
  }
}
