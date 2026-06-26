package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record ReplaceOtpChallengeCommand(
        String currentOtpChallengeId,
        Instant now) {
}