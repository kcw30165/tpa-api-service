package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentCapturedResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Strict safe snapshot codec for DETAILS_AND_OTP. */
public final class ForgotPasswordDetailsOtpReplayCodec {
  private static final String MEDIA_TYPE = "application/json";
  private final ActionTaggedIdempotencyResponseEnvelope envelope;
  private final ForgotPasswordDetailsOtpProtectedReplayPort protectedReplay;

  public ForgotPasswordDetailsOtpReplayCodec(
      ActionTaggedIdempotencyResponseEnvelope envelope,
      ForgotPasswordDetailsOtpProtectedReplayPort protectedReplay) {
    this.envelope = Objects.requireNonNull(envelope);
    this.protectedReplay = Objects.requireNonNull(protectedReplay);
  }

  public Mono<IdempotentCapturedResponse> capture(
      ForgotPasswordDetailsOtpResult result,
      HydratedIdempotentResponse response,
      ProtectedReplayReference reference) {
    Objects.requireNonNull(result);
    Objects.requireNonNull(response);
    validateCapturable(result);
    boolean success = result.status() == ForgotPasswordDetailsOtpResult.Status.SUCCESS;
    if (success != (reference != null)) {
      return Mono.error(
          new IllegalArgumentException("DETAILS_AND_OTP protected replay reference mismatch"));
    }
    if (response.httpStatus() != httpStatus(result.status())
        || !MEDIA_TYPE.equalsIgnoreCase(response.mediaType())
        || !response.safeHeaders().isEmpty()) {
      return Mono.error(
          new IllegalArgumentException("DETAILS_AND_OTP transient transport mismatch"));
    }
    SafePayload payload = new SafePayload(result.status().name(), List.copyOf(result.targets()));
    String safeBody =
        envelope.encode(ForgotPasswordIdempotencyAction.DETAILS_AND_OTP.action(), payload);
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, response.httpStatus(), MEDIA_TYPE, safeBody, Map.of(), reference);
    var hydrated = reference == null ? response : response.withProtectedReplayReference(reference);
    return Mono.just(new IdempotentCapturedResponse(hydrated, snapshot));
  }

  public Mono<ForgotPasswordDetailsOtpResult> hydrate(IdempotentTerminalResponseSnapshot snapshot) {
    Objects.requireNonNull(snapshot);
    if (snapshot.schemaVersion() != 1
        || !MEDIA_TYPE.equalsIgnoreCase(snapshot.mediaType())
        || !snapshot.safeHeaders().isEmpty()) {
      return Mono.error(
          new IllegalArgumentException("DETAILS_AND_OTP replay transport is invalid"));
    }
    SafePayload payload =
        envelope.decodePayload(
            ForgotPasswordIdempotencyAction.DETAILS_AND_OTP.action(),
            snapshot.serializedSafeBody(),
            SafePayload.class);
    ForgotPasswordDetailsOtpResult.Status status;
    try {
      status = ForgotPasswordDetailsOtpResult.Status.valueOf(payload.status());
    } catch (RuntimeException e) {
      return Mono.error(
          new IllegalArgumentException("DETAILS_AND_OTP replay status is unsupported"));
    }
    if (snapshot.httpStatus() != httpStatus(status)) {
      return Mono.error(
          new IllegalArgumentException("DETAILS_AND_OTP replay HTTP status mismatch"));
    }
    if (status == ForgotPasswordDetailsOtpResult.Status.SUCCESS) {
      if (snapshot.protectedReplayReference() == null) {
        return Mono.error(
            new IllegalArgumentException("DETAILS_AND_OTP success requires protected replay"));
      }
      return protectedReplay.resolve(snapshot.protectedReplayReference());
    }
    if (snapshot.protectedReplayReference() != null) {
      return Mono.error(
          new IllegalArgumentException("DETAILS_AND_OTP rejection cannot carry protected replay"));
    }
    var result =
        new ForgotPasswordDetailsOtpResult(
            status,
            null,
            0,
            0,
            payload.targets() == null ? List.of() : List.copyOf(payload.targets()));
    validateCapturable(result);
    return Mono.just(result);
  }

  public static int httpStatus(ForgotPasswordDetailsOtpResult.Status status) {
    return switch (status) {
      case SUCCESS -> 200;
      case VALIDATION_FAILED -> 400;
      case BUSINESS_REJECTED, EXPIRED -> 422;
      default -> throw new IllegalArgumentException("DETAILS_AND_OTP result is not capturable");
    };
  }

  private void validateCapturable(ForgotPasswordDetailsOtpResult result) {
    httpStatus(result.status());
    if (result.status() == ForgotPasswordDetailsOtpResult.Status.SUCCESS
        && (result.maskedDestination() == null
            || result.maskedDestination().isBlank()
            || result.expiresInSeconds() <= 0
            || result.resendAvailableInSeconds() < 0)) {
      throw new IllegalArgumentException("DETAILS_AND_OTP success result is invalid");
    }
    if (result.status() != ForgotPasswordDetailsOtpResult.Status.SUCCESS
        && (result.maskedDestination() != null
            || result.expiresInSeconds() != 0
            || result.resendAvailableInSeconds() != 0)) {
      throw new IllegalArgumentException("DETAILS_AND_OTP rejection contains protected data");
    }
  }

  public record SafePayload(String status, List<String> targets) {}
}
