package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record ReplaceOtpChallengeCommand(
        String currentOtpChallengeId,
        int length,
        boolean numericOnly,
        int ttlSeconds,
        int resendCooldownSeconds,
        boolean resendLimitEnabled,
        Instant now) {
}