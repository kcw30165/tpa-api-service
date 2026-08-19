package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;

public final class RedisForgotPasswordOtpCommitStateJsonMapper {
  private final ObjectMapper mapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public String toJson(ForgotPasswordOtpCommitState s) {
    try {
      return mapper.writeValueAsString(
          new RedisForgotPasswordOtpCommitStateRecord(
              s.authFlowSessionId(),
              s.otpChallengeId(),
              s.committedAt().toString(),
              s.challengeExpiresAt().toString()));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize forgot password OTP commit state");
    }
  }

  public ForgotPasswordOtpCommitState fromJson(String j) {
    try {
      var r = mapper.readValue(j, RedisForgotPasswordOtpCommitStateRecord.class);
      return new ForgotPasswordOtpCommitState(
          r.authFlowSessionId(),
          r.otpChallengeId(),
          Instant.parse(r.committedAt()),
          Instant.parse(r.challengeExpiresAt()));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to deserialize forgot password OTP commit state");
    }
  }
}
