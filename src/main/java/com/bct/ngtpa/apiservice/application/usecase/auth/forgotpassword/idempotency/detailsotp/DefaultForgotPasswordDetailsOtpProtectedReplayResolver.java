package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.detailsotp;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResolver;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Maps authoritative OTP commit/session/challenge state to DETAILS_AND_OTP replay. */
public final class DefaultForgotPasswordDetailsOtpProtectedReplayResolver
    implements ForgotPasswordDetailsOtpProtectedReplayPort {
  private final ForgotPasswordOtpReplayResolver replay;

  public DefaultForgotPasswordDetailsOtpProtectedReplayResolver(
      ForgotPasswordOtpReplayResolver replay) {
    this.replay = Objects.requireNonNull(replay);
  }

  @Override
  public Mono<ForgotPasswordDetailsOtpResult> resolve(ProtectedReplayReference reference) {
    Objects.requireNonNull(reference);
    return replay
        .resolve(reference.value())
        .map(
            result ->
                switch (result.status()) {
                  case ACTIVE ->
                      new ForgotPasswordDetailsOtpResult(
                          ForgotPasswordDetailsOtpResult.Status.SUCCESS,
                          result.maskedDestination(),
                          result.expiresInSeconds(),
                          result.retryAfterSeconds(),
                          List.of());
                  case EXPIRED ->
                      new ForgotPasswordDetailsOtpResult(
                          ForgotPasswordDetailsOtpResult.Status.EXPIRED,
                          null,
                          0,
                          0,
                          List.of("PAGE"));
                  case SYSTEM_ERROR -> ForgotPasswordDetailsOtpResult.system();
                });
  }
}
