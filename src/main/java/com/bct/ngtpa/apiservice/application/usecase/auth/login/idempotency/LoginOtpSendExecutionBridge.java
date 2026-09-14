package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient execution bridge for Login OTP Send. */
public final class LoginOtpSendExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final TransientJsonPort json;
  private final LoginOtpSendReplayCodec replayCodec;

  public LoginOtpSendExecutionBridge(TransientJsonPort json, LoginOtpSendReplayCodec replayCodec) {
    this.json = Objects.requireNonNull(json);
    this.replayCodec = Objects.requireNonNull(replayCodec);
  }

  public HydratedIdempotentResponse toTransientResponse(SendLoginOtpResult result) {
    Objects.requireNonNull(result, "Login OTP Send result must not be null");
    try {
      return new HydratedIdempotentResponse(
          LoginOtpSendReplayCodec.httpStatus(result.status()),
          MEDIA_TYPE,
          json.write(TransientWire.from(result)),
          Map.of());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Send transient encoding failed");
    }
  }

  public SendLoginOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response, "Login OTP Send transient response must not be null");
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType()) || !response.safeHeaders().isEmpty()) {
      throw new IllegalArgumentException("Login OTP Send transient transport is invalid");
    }
    try {
      TransientWire wire = json.read(response.serializedBody(), TransientWire.class);
      SendLoginOtpStatus status = SendLoginOtpStatus.valueOf(wire.status());
      if (response.httpStatus() != LoginOtpSendReplayCodec.httpStatus(status)) {
        throw new IllegalArgumentException("Login OTP Send transient HTTP status mismatch");
      }
      return wire.toResult(status);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Send transient decoding failed");
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
    static TransientWire from(SendLoginOtpResult result) {
      return new TransientWire(
          result.status().name(),
          result.maskedDestination(),
          result.expiresInSeconds(),
          result.resendCooldownSeconds(),
          result.retryAfterSeconds());
    }

    SendLoginOtpResult toResult(SendLoginOtpStatus status) {
      return new SendLoginOtpResult(
          status, maskedDestination, expiresInSeconds, resendCooldownSeconds, retryAfterSeconds);
    }
  }
}
