package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict safe snapshot codec for OTP_RESEND. */
public final class ForgotPasswordOtpResendReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ActionTaggedIdempotencyResponseEnvelope envelope;
  private final ForgotPasswordOtpResendProtectedReplayPort protectedReplay;

  public ForgotPasswordOtpResendReplayCodec(
      ActionTaggedIdempotencyResponseEnvelope envelope,
      ForgotPasswordOtpResendProtectedReplayPort protectedReplay) {
    this.envelope = Objects.requireNonNull(envelope);
    this.protectedReplay = Objects.requireNonNull(protectedReplay);
  }

  public Mono<IdempotentCapturedResponse> capture(
      ResendForgotPasswordOtpResult result,
      HydratedIdempotentResponse response,
      ProtectedReplayReference reference) {
    Objects.requireNonNull(result);
    Objects.requireNonNull(response);
    validateCapturable(result);
    boolean protectedResult = requiresProtectedReplay(result.status());
    if (protectedResult != (reference != null)) {
      return Mono.error(
          new IllegalArgumentException("OTP_RESEND protected replay reference mismatch"));
    }
    if (response.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()) {
      return Mono.error(new IllegalArgumentException("OTP_RESEND transient transport mismatch"));
    }
    String safeBody =
        envelope.encode(
            ForgotPasswordIdempotencyAction.OTP_RESEND.action(),
            new SafePayload(result.status().name()));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, response.httpStatus(), MEDIA_TYPE, safeBody, Map.of(), reference);
    var hydrated = reference == null ? response : response.withProtectedReplayReference(reference);
    return Mono.just(new IdempotentCapturedResponse(hydrated, snapshot));
  }

  public Mono<ResendForgotPasswordOtpResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (snapshot.schemaVersion() != 1
        || !MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())
        || !snapshot.safeHeaders().isEmpty()) {
      return Mono.error(new IllegalArgumentException("OTP_RESEND replay transport is invalid"));
    }
    SafePayload payload =
        envelope.decodePayload(
            ForgotPasswordIdempotencyAction.OTP_RESEND.action(),
            snapshot.serializedSafeBody(),
            SafePayload.class);
    ResendForgotPasswordOtpStatus status;
    try {
      status = ResendForgotPasswordOtpStatus.valueOf(payload.status());
    } catch (RuntimeException exception) {
      return Mono.error(new IllegalArgumentException("OTP_RESEND replay status is unsupported"));
    }
    if (snapshot.httpStatus() != httpStatus(status)) {
      return Mono.error(new IllegalArgumentException("OTP_RESEND replay HTTP status mismatch"));
    }
    if (requiresProtectedReplay(status)) {
      if (snapshot.protectedReplayReference() == null) {
        return Mono.error(
            new IllegalArgumentException("OTP_RESEND result requires protected replay"));
      }
      return protectedReplay.resolve(snapshot.protectedReplayReference());
    }
    if (snapshot.protectedReplayReference() != null) {
      return Mono.error(
          new IllegalArgumentException("OTP_RESEND rejection cannot carry protected replay"));
    }
    ResendForgotPasswordOtpResult result =
        new ResendForgotPasswordOtpResult(status, null, null, null, null, null);
    validateCapturable(result);
    return Mono.just(result);
  }

  public static int httpStatus(ResendForgotPasswordOtpStatus status) {
    return switch (status) {
      case RESENT, COMPLETED_REPLAY -> 200;
      case COOLDOWN_ACTIVE, RESEND_LIMIT_REACHED -> 429;
      case EXPIRED, AUTH_FLOW_INVALID -> 422;
      case SECURITY_REJECTED -> 403;
      case IDEMPOTENCY_KEY_REUSED -> 409;
      case DELIVERY_FAILED -> 502;
      default -> throw new IllegalArgumentException("OTP_RESEND result is not capturable");
    };
  }

  private static boolean requiresProtectedReplay(ResendForgotPasswordOtpStatus status) {
    return switch (status) {
      case RESENT, COMPLETED_REPLAY, EXPIRED -> true;
      default -> false;
    };
  }

  /**
   * Statuses whose live first response may carry the masked destination and timing. Only RESENT,
   * COMPLETED_REPLAY and EXPIRED are replayed from protected state (see {@link
   * #requiresProtectedReplay}); COOLDOWN_ACTIVE and RESEND_LIMIT_REACHED are safe rejections that
   * the protected operation returns without a replay reference, so only their status is persisted.
   */
  private static boolean carriesTransientContinuity(ResendForgotPasswordOtpStatus status) {
    return switch (status) {
      case RESENT, COMPLETED_REPLAY, EXPIRED, COOLDOWN_ACTIVE, RESEND_LIMIT_REACHED -> true;
      default -> false;
    };
  }

  private static void validateCapturable(ResendForgotPasswordOtpResult result) {
    httpStatus(result.status());
    if (!carriesTransientContinuity(result.status())
        && (result.maskedDestination() != null
            || result.expiresInSeconds() != null
            || result.resendCooldownSeconds() != null
            || result.remainingResendAttempts() != null
            || result.retryAfterSeconds() != null)) {
      throw new IllegalArgumentException("OTP_RESEND rejection contains protected continuity");
    }
  }

  public record SafePayload(String status) {}
}
