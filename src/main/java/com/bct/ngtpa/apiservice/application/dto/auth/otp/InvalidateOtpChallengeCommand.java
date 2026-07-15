package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record InvalidateOtpChallengeCommand(
        String otpChallengeId,
        OtpChallengeInvalidationReason reason,
        Instant now) {
}