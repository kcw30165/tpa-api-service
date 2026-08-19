package com.bct.ngtpa.apiservice.application.dto.security.ratelimit;

import java.util.Objects;

/**
 * Application-level rate-limit decision input.
 *
 * <p>The {@code bucketId} is an opaque caller-prepared identifier. It must not contain raw PII such
 * as email, mobile, phone number, IP address, registration session id, Redis keys, or request
 * payload data.
 */
public record RateLimitCommand(
    RateLimitBucket bucket,
    String bucketId,
    boolean enabled,
    int maxAttempts,
    int windowSeconds,
    String correlationId) {

  public RateLimitCommand {
    Objects.requireNonNull(bucket, "bucket must not be null");
    if (enabled) {
      if (bucketId == null || bucketId.isBlank()) {
        throw new IllegalArgumentException("bucketId must not be blank when rate limit is enabled");
      }
      if (maxAttempts <= 0) {
        throw new IllegalArgumentException(
            "maxAttempts must be greater than zero when rate limit is enabled");
      }
      if (windowSeconds <= 0) {
        throw new IllegalArgumentException(
            "windowSeconds must be greater than zero when rate limit is enabled");
      }
    }
  }
}
