package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpverify;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpStatus;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Strict public-result replay codec for OTP_VERIFY. Decoding requires FAIL_ON_UNKNOWN_PROPERTIES.
 */
public final class ForgotPasswordOtpVerifyReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ActionTaggedIdempotencyResponseEnvelope envelope;

  public ForgotPasswordOtpVerifyReplayCodec(ActionTaggedIdempotencyResponseEnvelope envelope) {
    this.envelope = Objects.requireNonNull(envelope);
  }

  public Mono<IdempotentCapturedResponse> capture(
      VerifyForgotPasswordOtpResult result, HydratedIdempotentResponse response) {
    Objects.requireNonNull(result);
    Objects.requireNonNull(response);
    validate(result);
    if (response.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()
        || response.protectedReplayReference() != null) {
      return Mono.error(new IllegalArgumentException("OTP_VERIFY transient transport mismatch"));
    }
    String body =
        envelope.encode(
            ForgotPasswordIdempotencyAction.OTP_VERIFY.action(),
            new SafePayload(
                result.status().name(),
                result.nextStep().name(),
                result.remainingVerifyAttempts()));
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, response.httpStatus(), MEDIA_TYPE, body, Map.of(), null);
    return Mono.just(new IdempotentCapturedResponse(response, snapshot));
  }

  public Mono<VerifyForgotPasswordOtpResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (snapshot.schemaVersion() != 1
        || !MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())
        || !snapshot.safeHeaders().isEmpty()
        || snapshot.protectedReplayReference() != null) {
      return Mono.error(new IllegalArgumentException("OTP_VERIFY replay transport is invalid"));
    }
    try {
      SafePayload payload =
          envelope.decodePayload(
              ForgotPasswordIdempotencyAction.OTP_VERIFY.action(),
              snapshot.serializedSafeBody(),
              SafePayload.class);
      var result =
          new VerifyForgotPasswordOtpResult(
              VerifyForgotPasswordOtpStatus.valueOf(payload.status()),
              WorkflowNextStep.valueOf(payload.nextStep()),
              payload.remainingVerifyAttempts());
      validate(result);
      if (snapshot.httpStatus() != httpStatus(result.status())) {
        throw new IllegalArgumentException("OTP_VERIFY replay HTTP status mismatch");
      }
      return Mono.just(result);
    } catch (RuntimeException exception) {
      return Mono.error(new IllegalArgumentException("OTP_VERIFY replay payload is invalid"));
    }
  }

  public static int httpStatus(VerifyForgotPasswordOtpStatus status) {
    return switch (status) {
      case VERIFIED, COMPLETED_REPLAY -> 200;
      case OTP_INVALID, AUTH_FLOW_INVALID, CSRF_INVALID -> 400;
      case OTP_EXPIRED, OTP_LOCKED -> 422;
      case DUPLICATE_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED -> 409;
      case SYSTEM_FAILED -> 500;
    };
  }

  private static void validate(VerifyForgotPasswordOtpResult result) {
    httpStatus(result.status());
    if ((result.status() == VerifyForgotPasswordOtpStatus.OTP_INVALID
            || result.status() == VerifyForgotPasswordOtpStatus.OTP_EXPIRED
            || result.status() == VerifyForgotPasswordOtpStatus.OTP_LOCKED)
        && result.remainingVerifyAttempts() == null) {
      throw new IllegalArgumentException("OTP_VERIFY attempt result requires remaining count");
    }
  }

  public record SafePayload(String status, String nextStep, Integer remainingVerifyAttempts) {}
}
