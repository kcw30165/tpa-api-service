package com.bct.ngtpa.apiservice.application.dto.auth.otp;

public record VerifyOtpChallengeResult(
                String otpChallengeId,
                OtpVerificationOutcome outcome,
                int remainingAttempts,
                Integer retryAfterSeconds) {
}