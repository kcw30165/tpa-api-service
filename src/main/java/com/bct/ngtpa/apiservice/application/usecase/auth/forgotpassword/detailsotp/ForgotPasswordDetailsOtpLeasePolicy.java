package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import java.time.Duration;

public record ForgotPasswordDetailsOtpLeasePolicy(
    int leaseSeconds,
    int redisAllowanceSeconds,
    int safetyMarginSeconds,
    int apimMaxAttempts,
    int apimTimeoutMillis,
    long apimBackoffMillis,
    int senderTimeoutMillis,
    int redisCommandTimeoutMillis) {
  public ForgotPasswordDetailsOtpLeasePolicy {
    if (leaseSeconds <= 0 || leaseSeconds > 120)
      throw new IllegalArgumentException(
          "auth-workflows.forgot-password.details-and-otp.mutation-lock-lease-seconds must be in range 1..120");
    if (redisAllowanceSeconds < 1 || redisAllowanceSeconds > 30)
      throw new IllegalArgumentException(
          "mutation-lock-redis-allowance-seconds must be in range 1..30");
    if (safetyMarginSeconds < 5 || safetyMarginSeconds > 30)
      throw new IllegalArgumentException(
          "mutation-lock-safety-margin-seconds must be in range 5..30");
    if (apimMaxAttempts <= 0
        || apimTimeoutMillis <= 0
        || apimBackoffMillis < 0
        || senderTimeoutMillis <= 0
        || redisCommandTimeoutMillis <= 0)
      throw new IllegalArgumentException(
          "details-and-otp external operation bounds must be finite and positive");
    long minimum =
        (long) apimMaxAttempts * apimTimeoutMillis
            + (long) Math.max(0, apimMaxAttempts - 1) * apimBackoffMillis
            + (long) senderTimeoutMillis
            + (long) redisAllowanceSeconds * 1000L
            + (long) safetyMarginSeconds * 1000L;
    if (minimum > 120000L)
      throw new IllegalArgumentException(
          "calculated details-and-otp minimum lease exceeds approved 120 seconds: " + minimum);
    if ((long) leaseSeconds * 1000L < minimum)
      throw new IllegalArgumentException(
          "configured details-and-otp mutation lock lease is below calculated minimum: lease="
              + leaseSeconds
              + ", minimumMillis="
              + minimum);
  }

  public long minimumLeaseMillis() {
    return (long) apimMaxAttempts * apimTimeoutMillis
        + (long) Math.max(0, apimMaxAttempts - 1) * apimBackoffMillis
        + (long) senderTimeoutMillis
        + (long) redisAllowanceSeconds * 1000L
        + (long) safetyMarginSeconds * 1000L;
  }

  public Duration lease() {
    return Duration.ofSeconds(leaseSeconds);
  }

  public Duration persistenceAllowance() {
    return Duration.ofSeconds(redisAllowanceSeconds);
  }
}
