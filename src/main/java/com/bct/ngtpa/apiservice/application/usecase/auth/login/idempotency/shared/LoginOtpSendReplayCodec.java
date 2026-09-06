package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict safe snapshot codec for Login OTP Send results. */
public final class LoginOtpSendReplayCodec {
  private static final int SCHEMA_VERSION = 1;
  private static final String MEDIA_TYPE = "application/json";
  private final LoginSharedResponseEnvelope envelope;

  public LoginOtpSendReplayCodec(LoginSharedResponseEnvelope envelope) {
    this.envelope = Objects.requireNonNull(envelope);
  }

  public Mono<IdempotentCapturedResponse> capture(
      SendLoginOtpResult result, HydratedIdempotentResponse transientResponse) {
    Objects.requireNonNull(result, "Login OTP Send result must not be null");
    Objects.requireNonNull(transientResponse, "Login OTP Send transient response must not be null");
    validateCapturable(result.status());
    if (transientResponse.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(transientResponse.mediaType())
        || !transientResponse.safeHeaders().isEmpty()) {
      throw new IllegalArgumentException("Login OTP Send transient transport mismatch");
    }
    String safeBody =
        envelope.encode(LoginSharedIdempotencyAction.OTP_SEND, SafePayload.from(result));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            SCHEMA_VERSION, transientResponse.httpStatus(), MEDIA_TYPE, safeBody, Map.of(), null);
    return Mono.just(new IdempotentCapturedResponse(transientResponse, snapshot));
  }

  public Mono<SendLoginOtpResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "Login OTP Send snapshot must not be null");
    if (snapshot.schemaVersion() != SCHEMA_VERSION) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Send replay schema is unsupported"));
    }
    if (!MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Send replay media type is invalid"));
    }
    if (!snapshot.safeHeaders().isEmpty()) {
      return Mono.error(new IllegalArgumentException("Login OTP Send replay headers are invalid"));
    }
    if (snapshot.protectedReplayReference() != null) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Send cannot carry protected replay"));
    }
    SafePayload payload =
        envelope.decodePayload(
            LoginSharedIdempotencyAction.OTP_SEND,
            snapshot.serializedSafeBody(),
            SafePayload.class);
    SendLoginOtpStatus status = status(payload.status());
    validateCapturable(status);
    if (snapshot.httpStatus() != httpStatus(status)) {
      return Mono.error(new IllegalArgumentException("Login OTP Send replay HTTP status mismatch"));
    }
    try {
      return Mono.just(
          new SendLoginOtpResult(
              status,
              payload.maskedDestination(),
              payload.expiresInSeconds(),
              payload.resendCooldownSeconds(),
              payload.retryAfterSeconds()));
    } catch (IllegalArgumentException exception) {
      return Mono.error(new IllegalArgumentException("Login OTP Send replay payload is invalid"));
    }
  }

  public static int httpStatus(SendLoginOtpStatus status) {
    return switch (status) {
      case SENT -> 200;
      case CSRF_INVALID -> 403;
      case AUTH_FLOW_INVALID, INVALID_DESTINATION -> 400;
      case DELIVERY_FAILED -> 502;
      default -> throw new IllegalArgumentException("Login OTP Send result is not capturable");
    };
  }

  private SendLoginOtpStatus status(String value) {
    try {
      return SendLoginOtpStatus.valueOf(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Send replay status is unsupported");
    }
  }

  private void validateCapturable(SendLoginOtpStatus status) {
    if (status == null
        || !(status == SendLoginOtpStatus.SENT
            || status == SendLoginOtpStatus.AUTH_FLOW_INVALID
            || status == SendLoginOtpStatus.INVALID_DESTINATION
            || status == SendLoginOtpStatus.DELIVERY_FAILED
            || status == SendLoginOtpStatus.CSRF_INVALID)) {
      throw new IllegalArgumentException("Login OTP Send result is not capturable");
    }
  }

  public record SafePayload(
      String status,
      String maskedDestination,
      Integer expiresInSeconds,
      Integer resendCooldownSeconds,
      Integer retryAfterSeconds) {
    static SafePayload from(SendLoginOtpResult result) {
      return new SafePayload(
          result.status().name(),
          result.maskedDestination(),
          result.expiresInSeconds(),
          result.resendCooldownSeconds(),
          result.retryAfterSeconds());
    }
  }
}
