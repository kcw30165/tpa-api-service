package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Idempotency TTL policy for registration auth-flow actions.
 *
 * <p>The properties intentionally live under the existing auth-workflows.registration root so
 * registration policy values remain in one place.
 */
@ConfigurationProperties(prefix = "auth-workflows.registration.idempotency")
@Getter
@Setter
public class RegistrationIdempotencyProperties {
  private int startTtlSeconds = 300;
  private int otpResendTtlSeconds = 300;
  private int otpVerifyTtlSeconds = 300;
  private int completeTtlSeconds = 300;

  public Duration startTtl() {
    return positiveDuration(startTtlSeconds, "start-ttl-seconds");
  }

  public Duration otpResendTtl() {
    return positiveDuration(otpResendTtlSeconds, "otp-resend-ttl-seconds");
  }

  public Duration otpVerifyTtl() {
    return positiveDuration(otpVerifyTtlSeconds, "otp-verify-ttl-seconds");
  }

  public Duration completeTtl() {
    return positiveDuration(completeTtlSeconds, "complete-ttl-seconds");
  }

  private Duration positiveDuration(int seconds, String propertyName) {
    if (seconds <= 0) {
      throw new IllegalStateException(
          "auth-workflows.registration.idempotency." + propertyName + " must be greater than zero");
    }
    return Duration.ofSeconds(seconds);
  }
}
