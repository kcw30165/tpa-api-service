package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Typed transient execution bridge for Login OTP Verify. */
public final class LoginOtpVerifySharedExecutionBridge {
  private static final String MEDIA_TYPE = "application/json";
  private final TransientJsonPort json;
  private final LoginOtpVerifyReplayCodec replayCodec;

  public LoginOtpVerifySharedExecutionBridge(
      TransientJsonPort json, LoginOtpVerifyReplayCodec replayCodec) {
    this.json = Objects.requireNonNull(json);
    this.replayCodec = Objects.requireNonNull(replayCodec);
  }

  public HydratedIdempotentResponse toTransientResponse(VerifyLoginOtpResult result) {
    Objects.requireNonNull(result, "Login OTP Verify result must not be null");
    try {
      return new HydratedIdempotentResponse(
          LoginOtpVerifyReplayCodec.httpStatus(result.status()),
          MEDIA_TYPE,
          json.write(TransientWire.from(result)),
          Map.of());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Verify transient encoding failed");
    }
  }

  public VerifyLoginOtpResult fromTransientResponse(HydratedIdempotentResponse response) {
    Objects.requireNonNull(response, "Login OTP Verify transient response must not be null");
    if (!MEDIA_TYPE.equalsIgnoreCase(response.mediaType()) || !response.safeHeaders().isEmpty()) {
      throw new IllegalArgumentException("Login OTP Verify transient transport is invalid");
    }
    try {
      TransientWire wire = json.read(response.serializedBody(), TransientWire.class);
      VerifyLoginOtpStatus status = VerifyLoginOtpStatus.valueOf(wire.status());
      if (response.httpStatus() != LoginOtpVerifyReplayCodec.httpStatus(status)) {
        throw new IllegalArgumentException("Login OTP Verify transient HTTP status mismatch");
      }
      return wire.toResult(status);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Verify transient decoding failed");
    }
  }

  public Mono<IdempotentCapturedResponse> capture(HydratedIdempotentResponse response) {
    return replayCodec.capture(
        fromTransientResponse(response), response, response.protectedReplayReference());
  }

  public Mono<IdempotentCapturedResponse> capture(
      HydratedIdempotentResponse response, ProtectedReplayReference protectedReplayReference) {
    return capture(response.withProtectedReplayReference(protectedReplayReference));
  }

  public Mono<HydratedIdempotentResponse> hydrate(
      IdempotentTerminalResponseSnapshot snapshot, String correlationId) {
    return replayCodec.hydrate(snapshot, correlationId).map(this::toTransientResponse);
  }

  private record TransientWire(
      String status,
      Integer remainingVerifyAttempts,
      boolean clearAuthFlowCookie,
      List<LoginSessionAccount> accounts,
      AuthenticatedSessionId authenticatedSessionId,
      String authenticatedCsrfToken) {
    static TransientWire from(VerifyLoginOtpResult result) {
      return new TransientWire(
          result.status().name(),
          result.remainingVerifyAttempts(),
          result.clearAuthFlowCookie(),
          result.accounts(),
          result.authenticatedSessionId(),
          result.authenticatedCsrfToken());
    }

    VerifyLoginOtpResult toResult(VerifyLoginOtpStatus status) {
      return new VerifyLoginOtpResult(
          status,
          remainingVerifyAttempts,
          clearAuthFlowCookie,
          accounts == null ? List.of() : accounts,
          authenticatedSessionId,
          authenticatedCsrfToken);
    }
  }
}
