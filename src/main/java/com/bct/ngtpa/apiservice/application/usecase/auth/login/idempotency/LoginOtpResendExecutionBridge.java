package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpStatus;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient execution bridge for Login OTP Resend. */
public final class LoginOtpResendExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final TransientJsonPort json;
  private final LoginOtpResendReplayCodec replayCodec;

  public LoginOtpResendExecutionBridge(
      TransientJsonPort json, LoginOtpResendReplayCodec replayCodec) {
    this.json = Objects.requireNonNull(json);
    this.replayCodec = Objects.requireNonNull(replayCodec);
  }

  public HydratedIdempotentResponse toTransientResponse(ResendLoginOtpResult result) {
    Objects.requireNonNull(result, "Login OTP Resend result must not be null");
    try {
      return new HydratedIdempotentResponse(
          LoginOtpResendReplayCodec.httpStatus(result.status()),
          MEDIA_TYPE,
          json.write(TransientWire.from(result)),
          LoginOtpResendReplayCodec.safeHeaders(result.retryAfterSeconds()));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Resend transient encoding failed");
    }
  }

  public ResendLoginOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response, "Login OTP Resend transient response must not be null");
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType())) {
      throw new IllegalArgumentException("Login OTP Resend transient media type is invalid");
    }
    try {
      TransientWire wire = json.read(response.serializedBody(), TransientWire.class);
      ResendLoginOtpStatus status = ResendLoginOtpStatus.valueOf(wire.status());
      var result = wire.toResult(status);
      if (response.httpStatus() != LoginOtpResendReplayCodec.httpStatus(status)
          || !response
              .safeHeaders()
              .equals(LoginOtpResendReplayCodec.safeHeaders(result.retryAfterSeconds()))) {
        throw new IllegalArgumentException("Login OTP Resend transient transport mismatch");
      }
      return result;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Resend transient decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return replayCodec.capture(fromTransientResponse(response), response);
  }

  public Mono<HydratedIdempotentResponse> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    return replayCodec.hydrate(snapshot).map(this::toTransientResponse);
  }

  private record TransientWire(
      String status,
      String maskedDestination,
      Integer expiresInSeconds,
      Integer resendCooldownSeconds,
      Integer retryAfterSeconds) {
    static TransientWire from(ResendLoginOtpResult result) {
      return new TransientWire(
          result.status().name(),
          result.maskedDestination(),
          result.expiresInSeconds(),
          result.resendCooldownSeconds(),
          result.retryAfterSeconds());
    }

    ResendLoginOtpResult toResult(ResendLoginOtpStatus status) {
      return new ResendLoginOtpResult(
          status, maskedDestination, expiresInSeconds, resendCooldownSeconds, retryAfterSeconds);
    }
  }
}
