package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import java.time.Instant;
import java.util.Objects;

public record ForgotPasswordOtpCommitState(
    String authFlowSessionId,
    String otpChallengeId,
    Instant committedAt,
    Instant challengeExpiresAt) {
  public ForgotPasswordOtpCommitState {
    Objects.requireNonNull(authFlowSessionId);
    Objects.requireNonNull(otpChallengeId);
    Objects.requireNonNull(committedAt);
    Objects.requireNonNull(challengeExpiresAt);
    if (!challengeExpiresAt.isAfter(committedAt))
      throw new IllegalArgumentException("challenge expiry must follow commit");
  }

  @Override
  public String toString() {
    return "ForgotPasswordOtpCommitState[references=<redacted>,committedAt="
        + committedAt
        + ",challengeExpiresAt="
        + challengeExpiresAt
        + "]";
  }
}
