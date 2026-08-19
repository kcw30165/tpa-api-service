package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth-workflows.login.otp-verify-recovery")
public class LoginOtpVerifyRetentionProperties {
  private int otpVerifyIdempotencyTtlSeconds = 600;
  private int authenticatedLoginHandoffRetentionSeconds = 900;
  private int otpConsumptionProofRetentionSeconds = 900;
  private int authFlowDeletionProofRetentionSeconds = 900;
  private int minimumRetentionSeconds = 300;
  private int maximumRetentionSeconds = 1800;

  public void validate() {
    range(otpVerifyIdempotencyTtlSeconds);
    range(authenticatedLoginHandoffRetentionSeconds);
    range(otpConsumptionProofRetentionSeconds);
    range(authFlowDeletionProofRetentionSeconds);
    if (authenticatedLoginHandoffRetentionSeconds < otpVerifyIdempotencyTtlSeconds
        || otpConsumptionProofRetentionSeconds < otpVerifyIdempotencyTtlSeconds)
      throw new IllegalStateException("proof and handoff retention must cover idempotency");
  }

  private void range(int v) {
    if (v < minimumRetentionSeconds || v > maximumRetentionSeconds)
      throw new IllegalStateException("login OTP verify retention outside approved bounds");
  }

  public Duration idempotencyTtl() {
    validate();
    return Duration.ofSeconds(otpVerifyIdempotencyTtlSeconds);
  }
}
