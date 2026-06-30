package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record CreateOtpChallengeCommand(
        OtpScenario scenario,
        String workflowRef,
        OtpChannel channel,
        String destinationHash,
        int length,
        boolean numericOnly,
        int ttlSeconds,
        int maxVerifyAttempts,
        int maxResendAttempts,
        Instant now) {
}