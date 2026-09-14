package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.otpresend;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpStatus;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Adapts protected OTP commit-state replay to the OTP_RESEND result contract. */
public final class DefaultForgotPasswordOtpResendProtectedReplayResolver
    implements ForgotPasswordOtpResendProtectedReplayPort {
  private final ForgotPasswordOtpReplayResolver replay;

  public DefaultForgotPasswordOtpResendProtectedReplayResolver(
      ForgotPasswordOtpReplayResolver replay) {
    this.replay = Objects.requireNonNull(replay);
  }

  @Override
  public Mono<ResendForgotPasswordOtpResult> resolve(ProtectedReplayReference reference) {
    Objects.requireNonNull(reference);
    return replay
        .resolve(reference.value())
        .map(DefaultForgotPasswordOtpResendProtectedReplayResolver::map);
  }

  private static ResendForgotPasswordOtpResult map(ForgotPasswordOtpReplayResult result) {
    return switch (result.status()) {
      case ACTIVE ->
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.COMPLETED_REPLAY,
              result.maskedDestination(),
              result.expiresInSeconds(),
              null,
              result.remainingResendAttempts(),
              result.retryAfterSeconds());
      case EXPIRED ->
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.EXPIRED,
              result.maskedDestination(),
              result.expiresInSeconds(),
              null,
              result.remainingResendAttempts(),
              result.retryAfterSeconds());
      case SYSTEM_ERROR ->
          new ResendForgotPasswordOtpResult(
              ResendForgotPasswordOtpStatus.SYSTEM_FAILED, null, null, null, null, null);
    };
  }
}
