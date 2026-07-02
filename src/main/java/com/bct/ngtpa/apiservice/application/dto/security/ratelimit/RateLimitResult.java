package com.bct.ngtpa.apiservice.application.dto.security.ratelimit;

import java.time.Instant;
import java.util.Objects;

public record RateLimitResult(
        RateLimitStatus status,
        RateLimitBucket bucket,
        int limit,
        int remaining,
        int retryAfterSeconds,
        Instant resetAt) {

    public RateLimitResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(bucket, "bucket must not be null");
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must not be negative");
        }
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException("retryAfterSeconds must not be negative");
        }
        if (status == RateLimitStatus.BLOCKED && retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retryAfterSeconds must be greater than zero when blocked");
        }
    }

    public static RateLimitResult allowed(
            RateLimitBucket bucket,
            int limit,
            int remaining,
            Instant resetAt) {
        return new RateLimitResult(
                RateLimitStatus.ALLOWED,
                bucket,
                limit,
                remaining,
                0,
                resetAt);
    }

    public static RateLimitResult blocked(
            RateLimitBucket bucket,
            int limit,
            int retryAfterSeconds,
            Instant resetAt) {
        return new RateLimitResult(
                RateLimitStatus.BLOCKED,
                bucket,
                limit,
                0,
                retryAfterSeconds,
                resetAt);
    }

    public static RateLimitResult disabled(RateLimitBucket bucket) {
        return new RateLimitResult(
                RateLimitStatus.DISABLED,
                bucket,
                0,
                0,
                0,
                null);
    }
}
