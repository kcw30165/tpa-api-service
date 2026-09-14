package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict safe snapshot codec for Login OTP Resend results. */
public final class LoginOtpResendReplayCodec {
  private static final int SCHEMA_VERSION = 1;
  private static final String MEDIA_TYPE = "application/json";
  private static final String RETRY_AFTER = "Retry-After";
  private final LoginResponseEnvelope envelope;

  public LoginOtpResendReplayCodec(LoginResponseEnvelope envelope) {
    this.envelope = Objects.requireNonNull(envelope);
  }

  public Mono<IdempotentCapturedResponse> capture(
      ResendLoginOtpResult result, HydratedIdempotentResponse transientResponse) {
    Objects.requireNonNull(result, "Login OTP Resend result must not be null");
    Objects.requireNonNull(
        transientResponse, "Login OTP Resend transient response must not be null");
    validateResult(result);
    Map<String, String> expectedHeaders = safeHeaders(result.retryAfterSeconds());
    if (transientResponse.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(transientResponse.mediaType())
        || !transientResponse.safeHeaders().equals(expectedHeaders)) {
      throw new IllegalArgumentException("Login OTP Resend transient transport mismatch");
    }
    String safeBody = envelope.encode(LoginIdempotencyAction.OTP_RESEND, SafePayload.from(result));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            SCHEMA_VERSION,
            transientResponse.httpStatus(),
            MEDIA_TYPE,
            safeBody,
            expectedHeaders,
            null);
    return Mono.just(new IdempotentCapturedResponse(transientResponse, snapshot));
  }

  public Mono<ResendLoginOtpResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "Login OTP Resend snapshot must not be null");
    if (snapshot.schemaVersion() != SCHEMA_VERSION) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Resend replay schema is unsupported"));
    }
    if (!MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Resend replay media type is invalid"));
    }
    if (snapshot.protectedReplayReference() != null) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Resend cannot carry protected replay"));
    }
    SafePayload payload =
        envelope.decodePayload(
            LoginIdempotencyAction.OTP_RESEND, snapshot.serializedSafeBody(), SafePayload.class);
    ResendLoginOtpStatus status = status(payload.status());
    var result = payload.toResult(status);
    try {
      validateResult(result);
    } catch (IllegalArgumentException exception) {
      return Mono.error(new IllegalArgumentException("Login OTP Resend replay payload is invalid"));
    }
    if (snapshot.httpStatus() != httpStatus(status)) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Resend replay HTTP status mismatch"));
    }
    if (!snapshot.safeHeaders().equals(safeHeaders(result.retryAfterSeconds()))) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Resend replay headers are invalid"));
    }
    return Mono.just(result);
  }

  public static int httpStatus(ResendLoginOtpStatus status) {
    return switch (status) {
      case RESENT -> 200;
      case CSRF_INVALID -> 403;
      case COOLDOWN_ACTIVE, LOCKED -> 429;
      case AUTH_FLOW_INVALID, EXPIRED, NOT_REPLACEABLE -> 422;
      case DELIVERY_FAILED -> 502;
      default -> throw new IllegalArgumentException("Login OTP Resend result is not capturable");
    };
  }

  public static Map<String, String> safeHeaders(Integer retryAfterSeconds) {
    if (retryAfterSeconds == null) return Map.of();
    if (retryAfterSeconds <= 0) {
      throw new IllegalArgumentException("Login OTP Resend retry-after must be positive");
    }
    return Map.of(RETRY_AFTER, Integer.toString(retryAfterSeconds));
  }

  private void validateResult(ResendLoginOtpResult result) {
    ResendLoginOtpStatus status = result.status();
    httpStatus(status);
    if (result.maskedDestination() != null && result.maskedDestination().isBlank()) {
      throw new IllegalArgumentException("Login OTP Resend masked destination is invalid");
    }
    if (result.expiresInSeconds() != null && result.expiresInSeconds() <= 0) {
      throw new IllegalArgumentException("Login OTP Resend expiry is invalid");
    }
    if (result.resendCooldownSeconds() != null && result.resendCooldownSeconds() < 0) {
      throw new IllegalArgumentException("Login OTP Resend cooldown is invalid");
    }
    safeHeaders(result.retryAfterSeconds());
    if (status == ResendLoginOtpStatus.RESENT
        && (blank(result.maskedDestination())
            || result.expiresInSeconds() == null
            || result.resendCooldownSeconds() == null
            || result.retryAfterSeconds() != null)) {
      throw new IllegalArgumentException("Login OTP Resend success payload is invalid");
    }
    if (status == ResendLoginOtpStatus.COOLDOWN_ACTIVE
        && (blank(result.maskedDestination())
            || result.expiresInSeconds() != null
            || result.resendCooldownSeconds() == null
            || result.retryAfterSeconds() == null)) {
      throw new IllegalArgumentException("Login OTP Resend cooldown payload is invalid");
    }
  }

  private ResendLoginOtpStatus status(String value) {
    try {
      return ResendLoginOtpStatus.valueOf(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Resend replay status is unsupported");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public record SafePayload(
      String status,
      String maskedDestination,
      Integer expiresInSeconds,
      Integer resendCooldownSeconds,
      Integer retryAfterSeconds) {
    static SafePayload from(ResendLoginOtpResult result) {
      return new SafePayload(
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
