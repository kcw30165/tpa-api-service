package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record CreateOtpChallengeResult(
        String otpChallengeId,
        String rawCode,
        Instant expiresAt,
        int expiresInSeconds) {
}