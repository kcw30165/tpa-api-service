package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record ReplaceOtpChallengeResult(
    String previousOtpChallengeId,
    String newOtpChallengeId,
    String rawCode,
    OtpReplacementOutcome outcome,
    Instant expiresAt,
    int expiresInSeconds,
    int resendCooldownSeconds,
    int remainingResendAttempts,
    Integer retryAfterSeconds) {}
