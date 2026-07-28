package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record OtpChallenge(
                String otpChallengeId,
                OtpScenario scenario,
                String workflowRef,
                OtpChannel channel,
                String destinationHash,
                String codeHash,
                OtpStatus status,
                int failedAttemptCount,
                int maxVerifyAttempts,
                int resendAttemptCount,
                int maxResendAttempts,
                Instant createdAt,
                Instant expiresAt,
                Instant lastSentAt,
                Instant invalidatedAt,
                Instant verifiedAt) {
}