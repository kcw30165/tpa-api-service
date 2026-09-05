package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict safe snapshot codec for Login OTP Verify results. */
public final class LoginOtpVerifyReplayCodec {
  private static final int SCHEMA_VERSION = 1;
  private static final String MEDIA_TYPE = "application/json";

  private final LoginSharedResponseEnvelope envelope = new LoginSharedResponseEnvelope();
  private final LoginOtpVerifyProtectedReplayResolver protectedReplayResolver;

  public LoginOtpVerifyReplayCodec(LoginOtpVerifyProtectedReplayResolver protectedReplayResolver) {
    this.protectedReplayResolver = Objects.requireNonNull(protectedReplayResolver);
  }

  public Mono<IdempotentCapturedResponse> capture(
      VerifyLoginOtpResult result,
      HydratedIdempotentResponse transientResponse,
      ProtectedReplayReference protectedReplayReference) {
    Objects.requireNonNull(result, "Login OTP Verify result must not be null");
    Objects.requireNonNull(
        transientResponse, "Login OTP Verify transient response must not be null");
    validateResult(result);
    validateReference(result.status(), protectedReplayReference);
    if (transientResponse.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(transientResponse.mediaType())
        || !transientResponse.safeHeaders().isEmpty()) {
      throw new IllegalArgumentException("Login OTP Verify transient transport mismatch");
    }

    String safeBody =
        envelope.encode(
            LoginSharedIdempotencyAction.OTP_VERIFY,
            new SafePayload(
                result.status().name(),
                result.remainingVerifyAttempts(),
                result.clearAuthFlowCookie()));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            SCHEMA_VERSION,
            transientResponse.httpStatus(),
            MEDIA_TYPE,
            safeBody,
            Map.of(),
            protectedReplayReference);
    return Mono.just(
        new IdempotentCapturedResponse(
            protectedReplayReference == null
                ? transientResponse
                : transientResponse.withProtectedReplayReference(protectedReplayReference),
            snapshot));
  }

  public Mono<VerifyLoginOtpResult> hydrate(
      IdempotentTerminalResponseSnapshot snapshot, String correlationId) {
    Objects.requireNonNull(snapshot, "Login OTP Verify snapshot must not be null");
    if (snapshot.schemaVersion() != SCHEMA_VERSION) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Verify replay schema is unsupported"));
    }
    if (!MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Verify replay media type is invalid"));
    }
    if (!snapshot.safeHeaders().isEmpty()) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Verify replay headers are invalid"));
    }

    SafePayload payload =
        envelope.decodePayload(
            LoginSharedIdempotencyAction.OTP_VERIFY,
            snapshot.serializedSafeBody(),
            SafePayload.class);
    VerifyLoginOtpStatus status = status(payload.status());
    if (snapshot.httpStatus() != httpStatus(status)) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Verify replay HTTP status mismatch"));
    }
    if (status == VerifyLoginOtpStatus.ACCOUNTS_READY) {
      if (!payload.clearAuthFlowCookie()
          || payload.remainingVerifyAttempts() != null
          || snapshot.protectedReplayReference() == null) {
        return Mono.error(
            new IllegalArgumentException("Login OTP Verify success snapshot is invalid"));
      }
      return protectedReplayResolver
          .resolve(snapshot.protectedReplayReference(), correlationId)
          .switchIfEmpty(
              Mono.error(
                  new IllegalArgumentException(
                      "Login OTP Verify protected replay recovery is unavailable")))
          .flatMap(
              recovered -> {
                try {
                  validateResult(recovered);
                  return recovered.status() == VerifyLoginOtpStatus.ACCOUNTS_READY
                      ? Mono.just(recovered)
                      : Mono.error(
                          new IllegalArgumentException(
                              "Login OTP Verify protected recovery result is invalid"));
                } catch (IllegalArgumentException exception) {
                  return Mono.error(
                      new IllegalArgumentException(
                          "Login OTP Verify protected recovery result is invalid"));
                }
              });
    }
    if (snapshot.protectedReplayReference() != null) {
      return Mono.error(
          new IllegalArgumentException("Login OTP Verify rejection cannot carry protected replay"));
    }
    VerifyLoginOtpResult result =
        new VerifyLoginOtpResult(
            status,
            payload.remainingVerifyAttempts(),
            payload.clearAuthFlowCookie(),
            List.of(),
            null,
            null);
    try {
      validateResult(result);
      return Mono.just(result);
    } catch (IllegalArgumentException exception) {
      return Mono.error(new IllegalArgumentException("Login OTP Verify replay payload is invalid"));
    }
  }

  public static int httpStatus(VerifyLoginOtpStatus status) {
    return switch (status) {
      case ACCOUNTS_READY -> 200;
      case CSRF_INVALID -> 403;
      case VERIFY_ATTEMPTS_EXCEEDED -> 429;
      case SESSION_CREATION_FAILED -> 503;
      case ACCOUNT_LIST_UNAVAILABLE -> 502;
      case ZERO_VALID_ACCOUNTS, INVALID, EXPIRED, AUTH_FLOW_INVALID -> 422;
      default -> throw new IllegalArgumentException("Login OTP Verify result is not capturable");
    };
  }

  private void validateResult(VerifyLoginOtpResult result) {
    httpStatus(result.status());
    if (result.status() == VerifyLoginOtpStatus.ACCOUNTS_READY) {
      if (!result.clearAuthFlowCookie()
          || result.accounts() == null
          || result.accounts().isEmpty()
          || result.authenticatedSessionId() == null
          || result.authenticatedCsrfToken() == null
          || result.authenticatedCsrfToken().isBlank()
          || result.remainingVerifyAttempts() != null) {
        throw new IllegalArgumentException("Login OTP Verify success result is invalid");
      }
      return;
    }
    if (result.accounts() != null && !result.accounts().isEmpty()
        || result.authenticatedSessionId() != null
        || result.authenticatedCsrfToken() != null) {
      throw new IllegalArgumentException(
          "Login OTP Verify rejection contains protected result data");
    }
    if (result.status() == VerifyLoginOtpStatus.INVALID
        && (result.remainingVerifyAttempts() == null
            || result.remainingVerifyAttempts() <= 0
            || result.clearAuthFlowCookie())) {
      throw new IllegalArgumentException("Login OTP Verify invalid result is inconsistent");
    }
    if (result.status() == VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED
        && (result.remainingVerifyAttempts() == null
            || result.remainingVerifyAttempts() != 0
            || !result.clearAuthFlowCookie())) {
      throw new IllegalArgumentException("Login OTP Verify exhausted result is inconsistent");
    }
  }

  private void validateReference(
      VerifyLoginOtpStatus status, ProtectedReplayReference protectedReplayReference) {
    if (status == VerifyLoginOtpStatus.ACCOUNTS_READY && protectedReplayReference == null) {
      throw new IllegalArgumentException("Login OTP Verify success requires protected replay");
    }
    if (status != VerifyLoginOtpStatus.ACCOUNTS_READY && protectedReplayReference != null) {
      throw new IllegalArgumentException(
          "Login OTP Verify rejection cannot carry protected replay");
    }
  }

  private VerifyLoginOtpStatus status(String value) {
    try {
      return VerifyLoginOtpStatus.valueOf(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Login OTP Verify replay status is unsupported");
    }
  }

  public record SafePayload(
      String status, Integer remainingVerifyAttempts, boolean clearAuthFlowCookie) {}
}
